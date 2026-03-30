package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"go.temporal.io/sdk/client"
	"go.uber.org/fx"

	"github.com/spongepowered/systemofadownload/api"
	"github.com/spongepowered/systemofadownload/internal/app"
	"github.com/spongepowered/systemofadownload/internal/httpapi"
	"github.com/spongepowered/systemofadownload/internal/otelsetup"
	"github.com/spongepowered/systemofadownload/internal/repository"
)

type Config struct {
	Port             string
	DatabaseURL      string
	TemporalHostPort string
	AdminToken       string
}

func NewConfig() *Config {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	dbURL := os.Getenv("DATABASE_URL")
	if dbURL == "" {
		dbURL = "postgres://postgres:password@localhost:5432/postgres?sslmode=disable" //nolint:gosec // development default
	}

	temporalHost := os.Getenv("TEMPORAL_HOST_PORT")
	if temporalHost == "" {
		temporalHost = "localhost:7233"
	}

	adminToken := os.Getenv("ADMIN_API_TOKEN")

	return &Config{
		Port:             port,
		DatabaseURL:      dbURL,
		TemporalHostPort: temporalHost,
		AdminToken:       adminToken,
	}
}

func NewTemporalClient(lc fx.Lifecycle, cfg *Config) (client.Client, error) {
	c, err := client.Dial(client.Options{
		HostPort: cfg.TemporalHostPort,
	})
	if err != nil {
		return nil, fmt.Errorf("creating temporal client: %w", err)
	}

	lc.Append(fx.Hook{
		OnStop: func(ctx context.Context) error {
			c.Close()
			return nil
		},
	})
	return c, nil
}

func NewDBPool(lc fx.Lifecycle, cfg *Config) (*pgxpool.Pool, error) {
	pool, err := pgxpool.New(context.Background(), cfg.DatabaseURL)
	if err != nil {
		return nil, err
	}

	lc.Append(fx.Hook{
		OnStop: func(ctx context.Context) error {
			pool.Close()
			return nil
		},
	})

	return pool, nil
}

func NewHTTPServer(lc fx.Lifecycle, cfg *Config, handler http.Handler, s fx.Shutdowner) *http.Server {
	srv := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           handler,
		ReadHeaderTimeout: 10 * time.Second,
	}

	lc.Append(fx.Hook{
		OnStart: func(ctx context.Context) error {
			slog.InfoContext(ctx, "starting HTTP server", "addr", ":"+cfg.Port)
			go func() {
				if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
					slog.Error("server failed", "error", err)
					_ = s.Shutdown()
				}
			}()
			return nil
		},
		OnStop: func(ctx context.Context) error {
			slog.InfoContext(ctx, "shutting down HTTP server")
			return srv.Shutdown(ctx)
		},
	})

	return srv
}

// OTelShutdown is a named type so fx can distinguish it from other func signatures.
type OTelShutdown func(context.Context) error

func NewOTelShutdown(lc fx.Lifecycle) OTelShutdown {
	shutdown, err := otelsetup.Setup(context.Background(), "soad-server")
	if err != nil {
		slog.Error("failed to initialize OpenTelemetry", "error", err)
		return func(context.Context) error { return nil }
	}

	lc.Append(fx.Hook{
		OnStop: func(ctx context.Context) error {
			slog.InfoContext(ctx, "shutting down OpenTelemetry")
			return shutdown(ctx)
		},
	})
	return shutdown
}

func NewMux(h *httpapi.Handler, cfg *Config, _ OTelShutdown) http.Handler {
	middlewares := []api.StrictMiddlewareFunc{
		httpapi.AdminAuthMiddleware(cfg.AdminToken),
	}
	apiHandler := api.NewStrictHandler(h, middlewares)
	mux := http.NewServeMux()

	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = fmt.Fprintln(w, "OK")
	})

	return otelhttp.NewHandler(api.HandlerFromMux(apiHandler, mux), "soad-server")
}

func main() {
	fx.New(
		fx.Provide(
			NewConfig,
			NewOTelShutdown,
			NewDBPool,
			NewTemporalClient,
			repository.NewRepository,
			func(c client.Client) httpapi.WorkflowStarter {
				return c
			},
			app.NewService,
			httpapi.NewHandler,
			NewMux,
			NewHTTPServer,
		),
		fx.Invoke(func(*http.Server) {}),
	).Run()
}
