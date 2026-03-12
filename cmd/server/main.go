package main

import (
	"context"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"

	"github.com/jackc/pgx/v5/pgxpool"
	"go.temporal.io/sdk/client"
	"go.uber.org/fx"

	"github.com/spongepowered/systemofadownload/api"
	"github.com/spongepowered/systemofadownload/internal/app"
	"github.com/spongepowered/systemofadownload/internal/httpapi"
	"github.com/spongepowered/systemofadownload/internal/repository"
)

type Config struct {
	Port             string
	DatabaseURL      string
	TemporalHostPort string
}

func NewConfig() *Config {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	dbURL := os.Getenv("DATABASE_URL")
	if dbURL == "" {
		dbURL = "postgres://postgres:password@localhost:5432/postgres?sslmode=disable"
	}

	temporalHost := os.Getenv("TEMPORAL_HOST_PORT")
	if temporalHost == "" {
		temporalHost = "localhost:7233"
	}

	return &Config{
		Port:             port,
		DatabaseURL:      dbURL,
		TemporalHostPort: temporalHost,
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
	// We use context.Background() here because the pool should live for the duration of the app.
	// fx.Lifecycle will handle closing it.
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
		Addr:    ":" + cfg.Port,
		Handler: handler,
	}

	lc.Append(fx.Hook{
		OnStart: func(ctx context.Context) error {
			fmt.Printf("Starting server on :%s\n", cfg.Port)
			go func() {
				if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
					log.Printf("Server failed: %v\n", err)
					_ = s.Shutdown()
				}
			}()
			return nil
		},
		OnStop: func(ctx context.Context) error {
			log.Println("Shutting down server...")
			// The ctx passed to OnStop has the timeout defined by fx (default 15s)
			return srv.Shutdown(ctx)
		},
	})

	return srv
}

func NewMux(h *httpapi.Handler) http.Handler {
	apiHandler := api.NewStrictHandler(h, nil)
	mux := http.NewServeMux()

	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		fmt.Fprintln(w, "OK")
	})

	return api.HandlerFromMux(apiHandler, mux)
}

func main() {
	fx.New(
		fx.Provide(
			NewConfig,
			NewDBPool,
			NewTemporalClient,
			func(pool *pgxpool.Pool) repository.Repository {
				return repository.NewRepository(pool)
			},
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
