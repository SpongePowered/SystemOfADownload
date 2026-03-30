// Command devtest-load runs the full SOAD pipeline end-to-end for SpongeVanilla:
// registration → version sync (fetch + index + order + enrich + changelog).
//
// Prerequisites:
//   - Temporal server running locally (localhost:7233)
//   - Docker running (for testcontainers PostgreSQL)
//   - Internet access (for Sonatype + git clone of SpongePowered repos)
//
// Usage:
//
//	go run ./cmd/devtest-load
package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"time"

	"github.com/golang-migrate/migrate/v4"
	_ "github.com/golang-migrate/migrate/v4/database/postgres"
	_ "github.com/golang-migrate/migrate/v4/source/file"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
	"github.com/testcontainers/testcontainers-go/wait"
	"go.temporal.io/sdk/client"
	"go.temporal.io/sdk/worker"

	"github.com/spongepowered/systemofadownload/api"
	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/app"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/gitcache"
	"github.com/spongepowered/systemofadownload/internal/httpapi"
	"github.com/spongepowered/systemofadownload/internal/repository"
	"github.com/spongepowered/systemofadownload/internal/sonatype"
	wf "github.com/spongepowered/systemofadownload/internal/workflow"
)

const (
	groupID      = "org.spongepowered"
	artifactID   = "spongevanilla"
	sonatypeBase = "https://repo.spongepowered.org"
	repoName     = "maven-public"
	taskQueue    = "version-sync"
	temporalAddr = "localhost:7233"
)

const mcPattern = `(?:\d+w\d+[a-z]|\d+\.\d+(?:\.\d+)?-snapshot-\d+|\d+\.\d+(?:\.\d+)?-(?:pre|rc)-?\d+|\d+\.\d+(?:\.\d+)?)`

var vanillaSchema = &domain.VersionSchema{
	UseMojangManifest: true,
	Variants: []domain.VersionFormatVariant{
		{
			Name:    "current",
			Pattern: `^(?P<minecraft>` + mcPattern + `)-(?P<api>\d+\.\d+\.\d+)(?:-(?P<qualifier>RC)(?P<build>\d+))?$`,
			Segments: []domain.SegmentRule{
				{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
				{Name: "api", ParseAs: "dotted", TagKey: "api"},
			},
		},
		{
			Name:    "beta-era",
			Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<api>\d+\.\d+\.\d+)-(?P<qualifier>BETA)-(?P<build>\d+)$`,
			Segments: []domain.SegmentRule{
				{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
				{Name: "api", ParseAs: "dotted", TagKey: "api"},
			},
		},
		{
			Name:    "snapshot",
			Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<api>\d+\.\d+\.\d+)-(?P<qualifier>SNAPSHOT)$`,
			Segments: []domain.SegmentRule{
				{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
				{Name: "api", ParseAs: "dotted", TagKey: "api"},
			},
		},
		{
			Name:    "dev-era",
			Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<api>\d+\.\d+(?:\.\d+)?)-(?P<qualifier>DEV)-(?P<build>\d+)$`,
			Segments: []domain.SegmentRule{
				{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
				{Name: "api", ParseAs: "dotted", TagKey: "api"},
			},
		},
		{
			Name:    "dev-era-compact",
			Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<api>\d+\.\d+(?:\.\d+)?)(?P<qualifier>DEV)-(?P<build>\d+)$`,
			Segments: []domain.SegmentRule{
				{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
				{Name: "api", ParseAs: "dotted", TagKey: "api"},
			},
		},
		{
			Name:    "bleeding-beta",
			Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-bleeding-(?P<qualifier>BETA)-(?P<build>\d+)$`,
			Segments: []domain.SegmentRule{
				{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
			},
		},
		{
			Name:    "bleeding-beta-intermediate",
			Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-bleeding-[\d.]+-(?P<qualifier>BETA)-(?P<build>\d+)$`,
			Segments: []domain.SegmentRule{
				{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
			},
		},
	},
}

func main() {
	slog.SetDefault(slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelInfo})))

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt)
	defer cancel()

	if err := run(ctx); err != nil {
		slog.Error("fatal error", "error", err)
		os.Exit(1)
	}
}

func run(ctx context.Context) error {
	// === Start PostgreSQL ===
	slog.InfoContext(ctx, "starting PostgreSQL container")
	pgContainer, err := postgres.Run(ctx,
		"postgres:16-alpine",
		postgres.WithDatabase("devtest"),
		postgres.WithUsername("devtest"),
		postgres.WithPassword("devtest"),
		testcontainers.WithWaitStrategy(
			wait.ForLog("database system is ready to accept connections").
				WithOccurrence(2).
				WithStartupTimeout(30*time.Second),
		),
	)
	if err != nil {
		return fmt.Errorf("starting postgres: %w", err)
	}
	defer func() { _ = pgContainer.Terminate(ctx) }()

	connStr, err := pgContainer.ConnectionString(ctx, "sslmode=disable")
	if err != nil {
		return fmt.Errorf("getting connection string: %w", err)
	}

	pool, err := pgxpool.New(ctx, connStr)
	if err != nil {
		return fmt.Errorf("creating pool: %w", err)
	}
	defer pool.Close()

	m, err := migrate.New("file://db/migrations", connStr)
	if err != nil {
		return fmt.Errorf("creating migrator: %w", err)
	}
	if err := m.Up(); err != nil && !errors.Is(err, migrate.ErrNoChange) {
		return fmt.Errorf("running migrations: %w", err)
	}
	slog.InfoContext(ctx, "PostgreSQL ready (migrations applied)")

	repo := repository.NewRepository(pool)
	svc := app.NewService(repo)

	// === Registration ===
	fmt.Println()
	fmt.Println("=== Registration ===")

	if err := svc.RegisterGroup(ctx, &domain.Group{
		GroupID: groupID,
		Name:    "SpongePowered",
		Website: new("https://spongepowered.org/"),
	}); err != nil {
		return fmt.Errorf("registering group: %w", err)
	}
	slog.InfoContext(ctx, "registered group", "groupID", groupID)

	if err := svc.RegisterArtifact(ctx, &domain.Artifact{
		GroupID:     groupID,
		ArtifactID:  artifactID,
		DisplayName: "SpongeVanilla",
		GitRepositories: []string{
			"https://github.com/SpongePowered/SpongeVanilla",
			"https://github.com/SpongePowered/Sponge",
		},
		Website: new("https://spongepowered.org/"),
		Issues:  new("https://github.com/SpongePowered/Sponge/issues"),
	}); err != nil {
		return fmt.Errorf("registering artifact: %w", err)
	}
	slog.InfoContext(ctx, "registered artifact", "artifactID", artifactID)

	if err := svc.UpdateVersionSchema(ctx, groupID, artifactID, vanillaSchema); err != nil {
		return fmt.Errorf("updating schema: %w", err)
	}
	slog.InfoContext(ctx, "stored version schema", "variants", len(vanillaSchema.Variants))

	// === Start worker ===
	fmt.Println()
	fmt.Println("=== Starting Worker ===")

	c, err := client.Dial(client.Options{HostPort: temporalAddr})
	if err != nil {
		return fmt.Errorf("dialing temporal: %w", err)
	}
	defer c.Close()

	gitCacheDir, err := os.MkdirTemp("", "soad-git-cache-*")
	if err != nil {
		return fmt.Errorf("creating git cache dir: %w", err)
	}
	defer func() { _ = os.RemoveAll(gitCacheDir) }()

	sonatypeClient := sonatype.NewHTTPClient(sonatypeBase, repoName)
	gitCache := gitcache.NewManager(gitCacheDir)

	w := worker.New(c, taskQueue, worker.Options{
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
	w.RegisterActivity(&activity.VersionSyncActivities{SonatypeClient: sonatypeClient, Repo: repo})
	w.RegisterActivity(&activity.VersionIndexActivities{SonatypeClient: sonatypeClient, Repo: repo, HTTPClient: http.DefaultClient})
	w.RegisterActivity(&activity.VersionOrderingActivities{Repo: repo, HTTPClient: http.DefaultClient})
	w.RegisterActivity(&activity.ChangelogActivities{Repo: repo})
	w.RegisterActivity(&activity.GitActivities{Cache: gitCache})

	if err := w.Start(); err != nil {
		return fmt.Errorf("starting worker: %w", err)
	}
	defer w.Stop()
	slog.InfoContext(ctx, "worker started")

	// === Start HTTP API server ===
	handler := httpapi.NewHandler(svc, c)
	apiHandler := api.NewStrictHandler(handler, nil)
	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = fmt.Fprintln(w, "OK")
	})
	httpHandler := api.HandlerFromMux(apiHandler, mux)

	srv := &http.Server{Addr: ":8080", Handler: httpHandler, ReadHeaderTimeout: 10 * time.Second}
	go func() {
		slog.Info("HTTP API server listening", "addr", ":8080")
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			slog.Error("HTTP server error", "error", err)
		}
	}()
	defer func() { _ = srv.Shutdown(ctx) }()

	// === Run VersionSyncWorkflow ===
	fmt.Println()
	fmt.Println("=== Running VersionSyncWorkflow ===")

	syncRun, err := c.ExecuteWorkflow(ctx, client.StartWorkflowOptions{
		ID:        fmt.Sprintf("version-sync-%s-%s", groupID, artifactID),
		TaskQueue: taskQueue,
	}, wf.VersionSyncWorkflow, wf.VersionSyncInput{
		GroupID:    groupID,
		ArtifactID: artifactID,
	})
	if err != nil {
		return fmt.Errorf("starting sync workflow: %w", err)
	}
	slog.InfoContext(ctx, "workflow started",
		"workflowID", syncRun.GetID(),
		"runID", syncRun.GetRunID())

	var result wf.VersionSyncOutput
	if err := syncRun.Get(ctx, &result); err != nil {
		return fmt.Errorf("sync workflow failed: %w", err)
	}

	// === Done ===
	fmt.Println()
	fmt.Println("=== Complete ===")
	fmt.Printf("New versions stored: %d\n", result.NewVersionsStored)
	fmt.Println()

	slog.InfoContext(ctx, "inspect workflows at http://localhost:8233")
	slog.InfoContext(ctx, "press Ctrl+C to shut down")
	<-ctx.Done()
	return nil
}
