package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/exaring/otelpgx"
	"github.com/go-slog/otelslog"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/spongepowered/systemofadownload/internal/logging"
	"go.temporal.io/sdk/client"
	temporalotel "go.temporal.io/sdk/contrib/opentelemetry"
	"go.temporal.io/sdk/interceptor"
	tlog "go.temporal.io/sdk/log"
	"go.temporal.io/sdk/worker"
	"go.temporal.io/sdk/workflow"
	"go.uber.org/fx"

	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/gitcache"
	"github.com/spongepowered/systemofadownload/internal/otelsetup"
	"github.com/spongepowered/systemofadownload/internal/repository"
	"github.com/spongepowered/systemofadownload/internal/sonatype"
	wf "github.com/spongepowered/systemofadownload/internal/workflow"
)

type Config struct {
	TemporalHostPort     string
	TemporalNamespace    string
	SonatypeBaseURL      string
	SonatypeRepoName     string
	SonatypeRepoDenyList []string
	DatabaseURL          string
	GitCacheDir          string
	MetricsPort          string
	BuildID              string
	PodName              string
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
	// Comma-separated list of hosted repository names whose assets must be
	// dropped from SearchAssets results. Used to suppress legacy hosted repos
	// (e.g. forge-proxy) that are still indexed under maven-public but return
	// 403 on direct download.
	var repoDeny []string
	if raw := strings.TrimSpace(os.Getenv("SONATYPE_REPO_DENY")); raw != "" {
		for _, r := range strings.Split(raw, ",") {
			if r = strings.TrimSpace(r); r != "" {
				repoDeny = append(repoDeny, r)
			}
		}
	}
	databaseURL := os.Getenv("DATABASE_URL")
	if databaseURL == "" {
		databaseURL = "postgres://localhost:5432/systemofadownload?sslmode=disable"
	}
	gitCacheDir := os.Getenv("GIT_CACHE_DIR")
	if gitCacheDir == "" {
		gitCacheDir = "/var/cache/soad/git"
	}
	metricsPort := os.Getenv("METRICS_PORT")
	if metricsPort == "" {
		metricsPort = "9090"
	}
	buildID := os.Getenv("BUILD_ID")
	if buildID == "" {
		slog.Error("BUILD_ID environment variable is required but not set")
		os.Exit(1)
	}
	podName := os.Getenv("POD_NAME")
	return &Config{
		TemporalHostPort:     hostPort,
		TemporalNamespace:    namespace,
		SonatypeBaseURL:      sonatypeURL,
		SonatypeRepoName:     repoName,
		SonatypeRepoDenyList: repoDeny,
		DatabaseURL:          databaseURL,
		GitCacheDir:          gitCacheDir,
		MetricsPort:          metricsPort,
		BuildID:              buildID,
		PodName:              podName,
	}
}

func NewOTel(lc fx.Lifecycle, cfg *Config, logs *logging.Result) *otelsetup.Result {
	result, err := otelsetup.Setup(context.Background(), "soad-worker")
	if err != nil {
		slog.Error("failed to initialize OpenTelemetry", "error", err)
		return &otelsetup.Result{
			Shutdown:       func(context.Context) error { return nil },
			MetricsHandler: http.NotFoundHandler(),
		}
	}

	slog.SetDefault(slog.New(otelslog.NewHandler(logs.Handler)))

	// Start metrics HTTP server for Prometheus scraping
	metricsMux := http.NewServeMux()
	metricsMux.Handle("/metrics", result.MetricsHandler)
	metricsSrv := &http.Server{
		Addr:              ":" + cfg.MetricsPort,
		Handler:           metricsMux,
		ReadHeaderTimeout: 5 * time.Second,
	}

	lc.Append(fx.Hook{
		OnStart: func(ctx context.Context) error {
			slog.InfoContext(ctx, "starting metrics server", "addr", ":"+cfg.MetricsPort)
			go func() {
				if err := metricsSrv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
					slog.Error("metrics server failed", "error", err)
				}
			}()
			return nil
		},
		OnStop: func(ctx context.Context) error {
			slog.InfoContext(ctx, "shutting down OpenTelemetry")
			_ = metricsSrv.Shutdown(ctx)
			_ = logs.Closer.Close()
			return result.Shutdown(ctx)
		},
	})
	return result
}

func NewTemporalClient(lc fx.Lifecycle, cfg *Config, _ *otelsetup.Result) (client.Client, error) {
	logger := tlog.NewStructuredLogger(slog.Default().With("component", "temporal"))

	tracingInterceptor, err := temporalotel.NewTracingInterceptor(temporalotel.TracerOptions{})
	if err != nil {
		return nil, fmt.Errorf("creating tracing interceptor: %w", err)
	}

	metricsHandler := temporalotel.NewMetricsHandler(temporalotel.MetricsHandlerOptions{})

	dialOpts := client.Options{
		HostPort:       cfg.TemporalHostPort,
		Namespace:      cfg.TemporalNamespace,
		Logger:         logger,
		MetricsHandler: metricsHandler,
		Interceptors:   []interceptor.ClientInterceptor{tracingInterceptor},
	}
	if cfg.PodName != "" {
		dialOpts.Identity = cfg.PodName
	}
	c, err := client.Dial(dialOpts)
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
	poolCfg, err := pgxpool.ParseConfig(cfg.DatabaseURL)
	if err != nil {
		return nil, fmt.Errorf("parsing database config: %w", err)
	}
	poolCfg.ConnConfig.Tracer = otelpgx.NewTracer()
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	pool, err := pgxpool.NewWithConfig(ctx, poolCfg)
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
	cfg *Config,
	syncActivities *activity.VersionSyncActivities,
	indexActivities *activity.VersionIndexActivities,
	orderingActivities *activity.VersionOrderingActivities,
	changelogActivities *activity.ChangelogActivities,
	gitActivities *activity.GitActivities,
) worker.Worker {
	w := worker.New(c, wf.VersionSyncTaskQueue, worker.Options{
		MaxConcurrentLocalActivityExecutionSize: 10,
		DeploymentOptions: worker.DeploymentOptions{
			UseVersioning: true,
			Version: worker.WorkerDeploymentVersion{
				DeploymentName: "soad-worker",
				BuildID:        cfg.BuildID,
			},
			DefaultVersioningBehavior: workflow.VersioningBehaviorAutoUpgrade,
		},
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
			NewTemporalClient,
			NewDatabasePool,
			repository.NewRepository,
			func(cfg *Config) sonatype.Client {
				return sonatype.NewHTTPClient(cfg.SonatypeBaseURL, cfg.SonatypeRepoName, cfg.SonatypeRepoDenyList...)
			},
			func(sc sonatype.Client, repo repository.Repository) *activity.VersionSyncActivities {
				return &activity.VersionSyncActivities{SonatypeClient: sc, Repo: repo}
			},
			activity.NewVersionIndexActivities,
			activity.NewVersionOrderingActivities,
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
