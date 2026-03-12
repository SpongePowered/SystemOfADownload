package main

import (
	"context"
	"fmt"
	"os"

	"go.temporal.io/sdk/client"
	"go.temporal.io/sdk/worker"
	"go.uber.org/fx"

	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/sonatype"
	wf "github.com/spongepowered/systemofadownload/internal/workflow"
)

type Config struct {
	TemporalHostPort  string
	TemporalNamespace string
	SonatypeBaseURL   string
	SonatypeRepoName  string
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
	return &Config{
		TemporalHostPort:  hostPort,
		TemporalNamespace: namespace,
		SonatypeBaseURL:   sonatypeURL,
		SonatypeRepoName:  repoName,
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

func NewTemporalWorker(lc fx.Lifecycle, c client.Client, activities *activity.VersionSyncActivities) worker.Worker {
	w := worker.New(c, wf.VersionSyncTaskQueue, worker.Options{})

	w.RegisterWorkflow(wf.VersionSyncWorkflow)
	w.RegisterActivity(activities)

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
			func(cfg *Config) sonatype.Client {
				return sonatype.NewHTTPClient(cfg.SonatypeBaseURL, cfg.SonatypeRepoName)
			},
			func(sc sonatype.Client) *activity.VersionSyncActivities {
				return &activity.VersionSyncActivities{SonatypeClient: sc}
			},
			NewTemporalWorker,
		),
		fx.Invoke(func(worker.Worker) {}),
	).Run()
}
