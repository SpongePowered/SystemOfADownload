package main

import (
	"context"
	"fmt"
	"net/http"
	"os"

	"github.com/jackc/pgx/v5/pgxpool"
	"go.temporal.io/sdk/client"
	"go.temporal.io/sdk/worker"
	"go.uber.org/fx"

	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/repository"
	"github.com/spongepowered/systemofadownload/internal/sonatype"
	wf "github.com/spongepowered/systemofadownload/internal/workflow"
)

type Config struct {
	TemporalHostPort  string
	TemporalNamespace string
	SonatypeBaseURL   string
	SonatypeRepoName  string
	DatabaseURL       string
}

func NewConfig() *Config {
	hostPort := os.Getenv("TEMPORAL_HOST_PORT")
	if hostPort == "" {
		hostPort = "localhost:7233"
	}
	namespace := os.Getenv("TEMPORAL_NAMESPACE")
	if namespace == "" {
		namespace = "default"
	}
	sonatypeURL := os.Getenv("SONATYPE_BASE_URL")
	if sonatypeURL == "" {
		sonatypeURL = "https://repo.spongepowered.org"
	}
	repoName := os.Getenv("SONATYPE_REPO_NAME")
	if repoName == "" {
		repoName = "maven-releases"
	}
	databaseURL := os.Getenv("DATABASE_URL")
	if databaseURL == "" {
		databaseURL = "postgres://localhost:5432/systemofadownload?sslmode=disable"
	}
	return &Config{
		TemporalHostPort:  hostPort,
		TemporalNamespace: namespace,
		SonatypeBaseURL:   sonatypeURL,
		SonatypeRepoName:  repoName,
		DatabaseURL:       databaseURL,
	}
}

func NewTemporalClient(lc fx.Lifecycle, cfg *Config) (client.Client, error) {
	c, err := client.Dial(client.Options{
		HostPort:  cfg.TemporalHostPort,
		Namespace: cfg.TemporalNamespace,
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

func NewDatabasePool(lc fx.Lifecycle, cfg *Config) (*pgxpool.Pool, error) {
	pool, err := pgxpool.New(context.Background(), cfg.DatabaseURL)
	if err != nil {
		return nil, fmt.Errorf("creating database pool: %w", err)
	}

	lc.Append(fx.Hook{
		OnStop: func(ctx context.Context) error {
			pool.Close()
			return nil
		},
	})
	return pool, nil
}

func NewTemporalWorker(
	lc fx.Lifecycle,
	c client.Client,
	syncActivities *activity.VersionSyncActivities,
	indexActivities *activity.VersionIndexActivities,
	orderingActivities *activity.VersionOrderingActivities,
) worker.Worker {
	w := worker.New(c, wf.VersionSyncTaskQueue, worker.Options{})

	w.RegisterWorkflow(wf.VersionSyncWorkflow)
	w.RegisterWorkflow(wf.VersionBatchIndexWorkflow)
	w.RegisterWorkflow(wf.VersionIndexWorkflow)
	w.RegisterWorkflow(wf.ExtractCommitBatchWorkflow)
	w.RegisterWorkflow(wf.ExtractCommitWorkflow)
	w.RegisterWorkflow(wf.VersionOrderingWorkflow)
	w.RegisterActivity(syncActivities)
	w.RegisterActivity(indexActivities)
	w.RegisterActivity(orderingActivities)

	lc.Append(fx.Hook{
		OnStart: func(ctx context.Context) error {
			fmt.Println("Starting Temporal worker...")
			return w.Start()
		},
		OnStop: func(ctx context.Context) error {
			fmt.Println("Stopping Temporal worker...")
			w.Stop()
			return nil
		},
	})

	return w
}

func main() {
	fx.New(
		fx.Provide(
			NewConfig,
			NewTemporalClient,
			NewDatabasePool,
			func(pool *pgxpool.Pool) repository.Repository {
				return repository.NewRepository(pool)
			},
			func(cfg *Config) sonatype.Client {
				return sonatype.NewHTTPClient(cfg.SonatypeBaseURL, cfg.SonatypeRepoName)
			},
			func(sc sonatype.Client, repo repository.Repository) *activity.VersionSyncActivities {
				return &activity.VersionSyncActivities{SonatypeClient: sc, Repo: repo}
			},
			func(sc sonatype.Client, repo repository.Repository) *activity.VersionIndexActivities {
				return &activity.VersionIndexActivities{
					SonatypeClient: sc,
					Repo:           repo,
					HTTPClient:     http.DefaultClient,
				}
			},
			func(repo repository.Repository) *activity.VersionOrderingActivities {
				return &activity.VersionOrderingActivities{
					Repo:       repo,
					HTTPClient: http.DefaultClient,
				}
			},
			NewTemporalWorker,
		),
		fx.Invoke(func(worker.Worker) {}),
	).Run()
}
