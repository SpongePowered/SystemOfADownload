package main

import (
    "context"
    "fmt"
    "log/slog"
    "net/http"
    "os"

    "github.com/jackc/pgx/v5/pgxpool"
    "go.temporal.io/sdk/client"
    tlog "go.temporal.io/sdk/log"
    "go.temporal.io/sdk/worker"
    "go.uber.org/fx"

    "github.com/spongepowered/systemofadownload/internal/activity"
    "github.com/spongepowered/systemofadownload/internal/gitcache"
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
    GitCacheDir       string
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
    gitCacheDir := os.Getenv("GIT_CACHE_DIR")
    if gitCacheDir == "" {
        gitCacheDir = "/var/cache/soad/git"
    }
    return &Config{
        TemporalHostPort:  hostPort,
        TemporalNamespace: namespace,
        SonatypeBaseURL:   sonatypeURL,
        SonatypeRepoName:  repoName,
        DatabaseURL:       databaseURL,
        GitCacheDir:       gitCacheDir,
    }
}

func NewTemporalClient(lc fx.Lifecycle, cfg *Config) (client.Client, error) {
    logger := tlog.NewStructuredLogger(slog.Default().With("component", "temporal"))
    c, err := client.Dial(client.Options{
        HostPort:  cfg.TemporalHostPort,
        Namespace: cfg.TemporalNamespace,
        Logger:    logger,
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
        changelogActivities *activity.ChangelogActivities,
        gitActivities *activity.GitActivities,
) worker.Worker {
    w := worker.New(c, wf.VersionSyncTaskQueue, worker.Options{
        MaxConcurrentLocalActivityExecutionSize: 10,
    })

    w.RegisterWorkflow(wf.VersionSyncWorkflow)
    w.RegisterWorkflow(wf.VersionBatchIndexWorkflow)
    w.RegisterWorkflow(wf.VersionIndexWorkflow)
    w.RegisterWorkflow(wf.VersionOrderingWorkflow)
    w.RegisterWorkflow(wf.CommitEnrichmentWorkflow)
    w.RegisterWorkflow(wf.EnrichmentBatchWorkflow)
    w.RegisterWorkflow(wf.EnrichVersionWorkflow)
    w.RegisterWorkflow(wf.ChangelogBatchWorkflow)
    w.RegisterWorkflow(wf.ChangelogVersionWorkflow)
    w.RegisterActivity(syncActivities)
    w.RegisterActivity(indexActivities)
    w.RegisterActivity(orderingActivities)
    w.RegisterActivity(changelogActivities)
    w.RegisterActivity(gitActivities)

    lc.Append(fx.Hook{
        OnStart: func(ctx context.Context) error {
            slog.InfoContext(ctx, "starting Temporal worker",
                "taskQueue", wf.VersionSyncTaskQueue)
            return w.Start()
        },
        OnStop: func(ctx context.Context) error {
            slog.InfoContext(ctx, "stopping Temporal worker")
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
            func(repo repository.Repository) *activity.ChangelogActivities {
                return &activity.ChangelogActivities{Repo: repo}
            },
            func(cfg *Config) *gitcache.Manager {
                return gitcache.NewManager(cfg.GitCacheDir)
            },
            func(cache *gitcache.Manager) *activity.GitActivities {
                return &activity.GitActivities{Cache: cache}
            },
            NewTemporalWorker,
        ),
        fx.Invoke(func(worker.Worker) {}),
    ).Run()
}
