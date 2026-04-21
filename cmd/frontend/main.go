package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"time"

	"github.com/exaring/otelpgx"
	"github.com/go-chi/httplog/v3"
	"github.com/go-slog/otelslog"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/klauspost/compress/gzhttp"
	"github.com/spongepowered/systemofadownload/internal/logging"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"go.uber.org/fx"

	"github.com/spongepowered/systemofadownload/internal/app"
	"github.com/spongepowered/systemofadownload/internal/frontend"
	"github.com/spongepowered/systemofadownload/internal/otelsetup"
	"github.com/spongepowered/systemofadownload/internal/repository"
)

type Config struct {
	Port              string
	DatabaseURL       string
	SponsorsCfgPath   string
	SponsorsAssetsDir string
}

func NewConfig() *Config {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8090"
	}

	dbURL := os.Getenv("DATABASE_URL")
	if dbURL == "" {
		dbURL = "postgres://postgres:password@localhost:5432/postgres?sslmode=disable" //nolint:gosec // development default
	}

	return &Config{
		Port:              port,
		DatabaseURL:       dbURL,
		SponsorsCfgPath:   os.Getenv("SPONSORS_CONFIG_PATH"),
		SponsorsAssetsDir: os.Getenv("SPONSORS_ASSETS_DIR"),
	}
}

// NewFrontendServer adapts the cmd-level Config into the frontend.ServerConfig
// the package constructor expects, so the env-var read stays in main.go and
// the frontend package remains free of os.Getenv calls.
func NewFrontendServer(cfg *Config, service *app.Service) (*frontend.Server, error) {
	return frontend.NewServer(service, frontend.ServerConfig{
		SponsorsCfgPath:   cfg.SponsorsCfgPath,
		SponsorsAssetsDir: cfg.SponsorsAssetsDir,
	})
}

func NewDBPool(lc fx.Lifecycle, cfg *Config) (*pgxpool.Pool, error) {
	poolCfg, err := pgxpool.ParseConfig(cfg.DatabaseURL)
	if err != nil {
		return nil, err
	}
	poolCfg.ConnConfig.Tracer = otelpgx.NewTracer()
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	pool, err := pgxpool.NewWithConfig(ctx, poolCfg)
	if err != nil {
		return nil, fmt.Errorf("creating pool: %w", err)
	}

	// Verify the connection works before the server starts accepting traffic.
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("database ping failed (check DATABASE_URL): %w", err)
	}
	slog.Info("database connection verified")

	lc.Append(fx.Hook{
		OnStop: func(ctx context.Context) error {
			pool.Close()
			return nil
		},
	})

	return pool, nil
}

func NewOTel(lc fx.Lifecycle, logs *logging.Result) *otelsetup.Result {
	result, err := otelsetup.Setup(context.Background(), "soad-frontend")
	if err != nil {
		slog.Error("failed to initialize OpenTelemetry", "error", err)
		return &otelsetup.Result{
			Shutdown:       func(context.Context) error { return nil },
			MetricsHandler: http.NotFoundHandler(),
		}
	}

	slog.SetDefault(slog.New(otelslog.NewHandler(logs.Handler)))

	lc.Append(fx.Hook{
		OnStop: func(ctx context.Context) error {
			slog.InfoContext(ctx, "shutting down OpenTelemetry")
			_ = logs.Closer.Close()
			return result.Shutdown(ctx)
		},
	})
	return result
}

func NewMux(fe *frontend.Server, otel *otelsetup.Result) http.Handler {
	mux := http.NewServeMux()
	fe.RegisterRoutes(mux)

	logger := slog.New(slog.NewJSONHandler(os.Stderr, nil)).
		With(slog.String("service", "soad-frontend"))

	handler := gzhttp.GzipHandler(otelhttp.NewHandler(mux, "soad-frontend"))
	loggedHandler := httplog.RequestLogger(logger, &httplog.Options{
		Level:             slog.LevelInfo,
		Schema:            httplog.SchemaOTEL,
		RecoverPanics:     true,
		LogRequestHeaders: []string{"User-Agent", "Referer"},
	})(handler)

	// Outer mux: /healthz and /metrics bypass tracing, gzip, and request logging
	// to keep probe/scrape traffic off the observability pipelines.
	outer := http.NewServeMux()
	outer.HandleFunc("GET /healthz", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = fmt.Fprintln(w, "OK")
	})
	outer.Handle("GET /metrics", otel.MetricsHandler)
	outer.Handle("/", loggedHandler)

	return outer
}

func NewHTTPServer(lc fx.Lifecycle, cfg *Config, handler http.Handler, s fx.Shutdowner) *http.Server {
	srv := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           handler,
		ReadHeaderTimeout: 10 * time.Second,
	}

	lc.Append(fx.Hook{
		OnStart: func(ctx context.Context) error {
			slog.InfoContext(ctx, "starting frontend server", "addr", ":"+cfg.Port)
			go func() {
				if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
					slog.Error("server failed", "error", err)
					_ = s.Shutdown()
				}
			}()
			return nil
		},
		OnStop: func(ctx context.Context) error {
			slog.InfoContext(ctx, "shutting down frontend server")
			return srv.Shutdown(ctx)
		},
	})

	return srv
}

func main() {
	logs, err := logging.Setup()
	if err != nil {
		slog.Error("failed to initialize logging", "error", err)
		os.Exit(1)
	}

	fx.New(
		fx.WithLogger(logging.FxLogger),
		fx.StartTimeout(60*time.Second),
		fx.Supply(logs),
		fx.Provide(
			NewConfig,
			NewOTel,
			NewDBPool,
			repository.NewRepository,
			app.NewService,
			NewFrontendServer,
			NewMux,
			NewHTTPServer,
		),
		fx.Invoke(func(*http.Server) {}),
	).Run()
}
