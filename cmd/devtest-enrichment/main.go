// Command devtest-enrichment tests the commit enrichment pipeline end-to-end.
//
// It seeds ALL SpongeVanilla versions from Sonatype, runs version ordering to
// establish sort_order, then picks specific target versions (the latest for
// MC 1.21.11 plus N-1, N-2, N-10) and runs the asset indexing + commit
// extraction + enrichment pipeline on just those versions.
//
// Prerequisites:
//   - Temporal server running locally (localhost:7233)
//   - Docker running (for testcontainers PostgreSQL)
//   - Internet access (for Sonatype + git clone of SpongePowered repos)
//
// Usage:
//
//	go run ./cmd/devtest-enrichment
package main

import (
	"context"
	"encoding/json"
	"encoding/xml"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strings"
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

	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/gitcache"
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
	// --- Start PostgreSQL ---
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

	// --- Seed ALL versions from Sonatype ---
	if err := seedAllVersions(ctx, repo); err != nil {
		return fmt.Errorf("seeding: %w", err)
	}

	// --- Connect to Temporal ---
	slog.InfoContext(ctx, "connecting to Temporal", "addr", temporalAddr)
	c, err := client.Dial(client.Options{HostPort: temporalAddr})
	if err != nil {
		return fmt.Errorf("dialing temporal: %w", err)
	}
	defer c.Close()

	// --- Set up git cache ---
	gitCacheDir, err := os.MkdirTemp("", "soad-git-cache-*")
	if err != nil {
		return fmt.Errorf("creating git cache dir: %w", err)
	}
	defer func() { _ = os.RemoveAll(gitCacheDir) }()
	slog.InfoContext(ctx, "git cache dir", "path", gitCacheDir)

	// --- Register worker ---
	sonatypeClient := sonatype.NewHTTPClient(sonatypeBase, repoName)
	gitCache := gitcache.NewManager(gitCacheDir)

	w := worker.New(c, taskQueue, worker.Options{
		MaxConcurrentLocalActivityExecutionSize: 4,
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

	w.RegisterActivity(&activity.VersionSyncActivities{
		SonatypeClient: sonatypeClient,
		Repo:           repo,
	})
	w.RegisterActivity(activity.NewVersionIndexActivities(sonatypeClient, repo))
	w.RegisterActivity(activity.NewVersionOrderingActivities(repo))
	w.RegisterActivity(&activity.ChangelogActivities{Repo: repo})
	w.RegisterActivity(&activity.GitActivities{Cache: gitCache})

	if err := w.Start(); err != nil {
		return fmt.Errorf("starting worker: %w", err)
	}
	defer w.Stop()
	slog.InfoContext(ctx, "worker started")

	// --- Step 1: Run VersionOrderingWorkflow on ALL versions ---
	slog.InfoContext(ctx, "running VersionOrderingWorkflow for all versions")
	orderRun, err := c.ExecuteWorkflow(ctx, client.StartWorkflowOptions{
		ID:        "devtest-ordering",
		TaskQueue: taskQueue,
	}, wf.VersionOrderingWorkflow, wf.VersionOrderingInput{
		GroupID:    groupID,
		ArtifactID: artifactID,
	})
	if err != nil {
		return fmt.Errorf("starting ordering: %w", err)
	}
	var orderResult wf.VersionOrderingOutput
	if err := orderRun.Get(ctx, &orderResult); err != nil {
		return fmt.Errorf("ordering failed: %w", err)
	}
	slog.InfoContext(ctx, "ordering completed", "versionsOrdered", orderResult.VersionsOrdered)

	// --- Step 2: Pick target versions ---
	// Get all versions sorted by sort_order DESC (newest first).
	allVersions, err := repo.ListArtifactVersions(ctx, db.ListArtifactVersionsParams{
		GroupID:    groupID,
		ArtifactID: artifactID,
	})
	if err != nil {
		return fmt.Errorf("listing versions: %w", err)
	}

	// Find versions tagged with minecraft=1.21.11
	targets := pickTargetVersions(ctx, repo, allVersions)
	if len(targets) == 0 {
		return fmt.Errorf("no target versions found — check if MC 1.21.11 versions exist")
	}

	fmt.Println()
	fmt.Println("=== Target Versions for Enrichment ===")
	for _, t := range targets {
		fmt.Printf("  sort=%04d  %s\n", t.SortOrder, t.Version)
	}
	fmt.Println()

	// --- Step 3: Index assets + extract commits for target versions ---
	var versionInfos []domain.VersionInfo
	for _, t := range targets {
		versionInfos = append(versionInfos, domain.VersionInfo{
			GroupID:    groupID,
			ArtifactID: artifactID,
			Version:    t.Version,
		})
	}

	slog.InfoContext(ctx, "indexing assets for target versions", "count", len(versionInfos))
	batchRun, err := c.ExecuteWorkflow(ctx, client.StartWorkflowOptions{
		ID:        "devtest-batch-index",
		TaskQueue: taskQueue,
	}, wf.VersionBatchIndexWorkflow, wf.VersionBatchIndexInput{
		Versions:   versionInfos,
		WindowSize: 3,
	})
	if err != nil {
		return fmt.Errorf("starting batch index: %w", err)
	}
	var batchResult int
	if err := batchRun.Get(ctx, &batchResult); err != nil {
		return fmt.Errorf("batch index failed: %w", err)
	}
	slog.InfoContext(ctx, "batch indexing completed", "processed", batchResult)

	// --- Print commit SHAs extracted from JARs ---
	fmt.Println()
	fmt.Println("=== Extracted Commit SHAs ===")
	for _, t := range targets {
		av, err := repo.GetArtifactVersion(ctx, db.GetArtifactVersionParams{
			GroupID: groupID, ArtifactID: artifactID, Version: t.Version,
		})
		if err != nil {
			continue
		}
		if av.CommitBody != nil {
			var info domain.CommitInfo
			if err := json.Unmarshal(av.CommitBody, &info); err == nil {
				fmt.Printf("  %-45s  sha=%.12s  repo=%s  branch=%s\n",
					t.Version, info.Sha, info.Repository, info.Branch)
			}
		} else {
			fmt.Printf("  %-45s  (no commit extracted)\n", t.Version)
		}
	}
	fmt.Println()

	// --- Step 4: Run CommitEnrichmentWorkflow ---
	slog.InfoContext(ctx, "running CommitEnrichmentWorkflow")
	enrichRun, err := c.ExecuteWorkflow(ctx, client.StartWorkflowOptions{
		ID:        "devtest-enrichment",
		TaskQueue: taskQueue,
	}, wf.CommitEnrichmentWorkflow, wf.CommitEnrichmentInput{
		GroupID:    groupID,
		ArtifactID: artifactID,
	})
	if err != nil {
		return fmt.Errorf("starting enrichment: %w", err)
	}
	var enrichResult wf.CommitEnrichmentOutput
	if err := enrichRun.Get(ctx, &enrichResult); err != nil {
		return fmt.Errorf("enrichment failed: %w", err)
	}
	slog.InfoContext(ctx, "enrichment completed",
		"versionsEnriched", enrichResult.VersionsEnriched,
		"changelogsComputed", enrichResult.ChangelogsComputed)

	// --- Print enrichment results ---
	fmt.Println()
	fmt.Println("=== Enrichment Results ===")
	fmt.Println()

	for _, t := range targets {
		av, err := repo.GetArtifactVersion(ctx, db.GetArtifactVersionParams{
			GroupID: groupID, ArtifactID: artifactID, Version: t.Version,
		})
		if err != nil || av.CommitBody == nil {
			fmt.Printf("%-45s  (no data)\n\n", t.Version)
			continue
		}

		var info domain.CommitInfo
		if err := json.Unmarshal(av.CommitBody, &info); err != nil {
			fmt.Printf("%-45s  (unmarshal error: %v)\n\n", t.Version, err)
			continue
		}

		fmt.Printf("sort=%04d  %s\n", av.SortOrder, av.Version)
		fmt.Printf("  sha:      %s\n", info.Sha)
		fmt.Printf("  repo:     %s\n", info.Repository)
		fmt.Printf("  enriched: %v\n", info.EnrichedAt != "")

		if info.Author != nil {
			fmt.Printf("  author:   %s <%s>\n", info.Author.Name, info.Author.Email)
		}
		if info.Message != "" {
			fmt.Printf("  message:  %.100s\n", info.Message)
		}
		if info.CommitDate != "" {
			fmt.Printf("  date:     %s\n", info.CommitDate)
		}

		for _, sub := range info.Submodules {
			fmt.Printf("  submodule: %s\n", sub.Repository)
			fmt.Printf("    sha=%.12s", sub.Sha)
			if sub.Message != "" {
				fmt.Printf("  msg=%.60s", sub.Message)
			}
			fmt.Println()
		}

		if info.Changelog != nil {
			fmt.Printf("  changelog: %d commits (prev=%s)\n",
				len(info.Changelog.Commits), info.Changelog.PreviousVersion[:min(12, len(info.Changelog.PreviousVersion))])
			for i, commit := range info.Changelog.Commits {
				if i >= 5 {
					fmt.Printf("    ... and %d more\n", len(info.Changelog.Commits)-5)
					break
				}
				fmt.Printf("    %.12s  %s\n", commit.Sha, commit.Message)
			}
			printSubmoduleChangelogs(info.Changelog.SubmoduleChangelogs, "  ")
		}

		if info.ChangelogStatus != "" {
			fmt.Printf("  changelogStatus: %s\n", info.ChangelogStatus)
		}

		fmt.Println()
	}

	slog.InfoContext(ctx, "inspect workflows at http://localhost:8233")
	slog.InfoContext(ctx, "press Ctrl+C to shut down")
	<-ctx.Done()
	return nil
}

// pickTargetVersions selects versions for enrichment testing.
// It picks specific named versions plus all versions for a target MC version.
func pickTargetVersions(ctx context.Context, repo repository.Repository, allVersions []db.ArtifactVersion) []db.ArtifactVersion {
	// allVersions is sorted by sort_order DESC (newest first).

	// Specific versions we want to compare (1.12.2 era: 7.1.7 release vs 7.3.0)
	namedVersions := []string{
		"1.12.2-7.1.7",
		"1.12.2-7.3.0",
	}

	// Also grab all MC 1.21.11 versions
	targetMC := "1.21.11"
	mcVersions := versionsWithTag(ctx, repo, allVersions, "minecraft", targetMC)
	if len(mcVersions) == 0 {
		slog.WarnContext(ctx, "no versions found for MC version, trying fallback",
			"tried", targetMC, "fallback", "1.21.10")
		targetMC = "1.21.10"
		mcVersions = versionsWithTag(ctx, repo, allVersions, "minecraft", targetMC)
	}

	// Combine: named versions + MC versions, deduped
	seen := make(map[string]bool)
	var result []db.ArtifactVersion

	// Add named versions (find them in allVersions to get their sort_order)
	for _, name := range namedVersions {
		for _, v := range allVersions {
			if v.Version == name && !seen[name] {
				result = append(result, v)
				seen[name] = true
				break
			}
		}
	}

	// Also add the versions immediately adjacent to named versions (N-1, N+1)
	// so we get changelog context
	for _, name := range namedVersions {
		for i, v := range allVersions {
			if v.Version == name {
				// N-1 (next in list since sorted DESC)
				if i+1 < len(allVersions) && !seen[allVersions[i+1].Version] {
					result = append(result, allVersions[i+1])
					seen[allVersions[i+1].Version] = true
				}
				// N+1 (previous in list)
				if i > 0 && !seen[allVersions[i-1].Version] {
					result = append(result, allVersions[i-1])
					seen[allVersions[i-1].Version] = true
				}
				break
			}
		}
	}

	for _, v := range mcVersions {
		if !seen[v.Version] {
			result = append(result, v)
			seen[v.Version] = true
		}
	}

	slog.InfoContext(ctx, "selected target versions",
		"named", len(namedVersions), "minecraft", targetMC,
		"mcVersions", len(mcVersions), "total", len(result))
	return result
}

func versionsWithTag(ctx context.Context, repo repository.Repository, allVersions []db.ArtifactVersion, tagKey, tagValue string) []db.ArtifactVersion {
	var result []db.ArtifactVersion
	for _, v := range allVersions {
		tags, err := repo.ListArtifactVersionTags(ctx, v.ID)
		if err != nil {
			continue
		}
		for _, t := range tags {
			if t.TagKey == tagKey && t.TagValue == tagValue {
				result = append(result, v)
				break
			}
		}
	}
	return result
}

func seedAllVersions(ctx context.Context, repo repository.Repository) error {
	slog.InfoContext(ctx, "seeding group and artifact")

	if err := repo.WithTx(ctx, func(tx repository.Tx) error {
		_, err := tx.CreateGroup(ctx, db.CreateGroupParams{
			MavenID: groupID,
			Name:    "SpongePowered",
		})
		return err
	}); err != nil {
		return fmt.Errorf("creating group: %w", err)
	}

	schemaJSON, err := json.Marshal(vanillaSchema)
	if err != nil {
		return fmt.Errorf("marshaling schema: %w", err)
	}

	if err := repo.WithTx(ctx, func(tx repository.Tx) error {
		_, err := tx.CreateArtifact(ctx, db.CreateArtifactParams{
			GroupID:    groupID,
			ArtifactID: artifactID,
			Name:       "SpongeVanilla",
			GitRepositories: []byte(`[
				"https://github.com/SpongePowered/SpongeVanilla",
				"https://github.com/SpongePowered/SpongeCommon",
				"https://github.com/SpongePowered/SpongeAPI"
			]`),
			VersionSchema: schemaJSON,
		})
		return err
	}); err != nil {
		return fmt.Errorf("creating artifact: %w", err)
	}

	// Fetch ALL versions from Sonatype
	versions, err := fetchMavenVersions(ctx, artifactID)
	if err != nil {
		return fmt.Errorf("fetching versions: %w", err)
	}
	slog.InfoContext(ctx, "fetched versions from Sonatype", "count", len(versions))

	artifact, err := repo.GetArtifactByGroupAndId(ctx, db.GetArtifactByGroupAndIdParams{
		GroupID:    groupID,
		ArtifactID: artifactID,
	})
	if err != nil {
		return fmt.Errorf("looking up artifact: %w", err)
	}

	// Seed in batches
	const batchSize = 500
	for i := 0; i < len(versions); i += batchSize {
		end := i + batchSize
		if end > len(versions) {
			end = len(versions)
		}
		batch := versions[i:end]

		if err := repo.WithTx(ctx, func(tx repository.Tx) error {
			for _, v := range batch {
				_, err := tx.CreateArtifactVersion(ctx, db.CreateArtifactVersionParams{
					ArtifactID: artifact.ID,
					Version:    v,
				})
				if err != nil {
					return fmt.Errorf("inserting version %q: %w", v, err)
				}
			}
			return nil
		}); err != nil {
			return fmt.Errorf("seeding batch %d-%d: %w", i, end, err)
		}
	}
	slog.InfoContext(ctx, "seeded all versions", "count", len(versions))
	return nil
}

type mavenMetadata struct {
	Versioning struct {
		Versions struct {
			Version []string `xml:"version"`
		} `xml:"versions"`
	} `xml:"versioning"`
}

func fetchMavenVersions(ctx context.Context, artifact string) ([]string, error) {
	groupPath := strings.ReplaceAll(groupID, ".", "/")
	url := fmt.Sprintf("%s/repository/%s/%s/%s/maven-metadata.xml",
		sonatypeBase, repoName, groupPath, artifact)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, http.NoBody)
	if err != nil {
		return nil, err
	}

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("unexpected status %d from %s", resp.StatusCode, url)
	}

	var meta mavenMetadata
	if err := xml.NewDecoder(resp.Body).Decode(&meta); err != nil {
		return nil, fmt.Errorf("decoding maven-metadata.xml: %w", err)
	}

	return meta.Versioning.Versions.Version, nil
}

func printSubmoduleChangelogs(subs map[string]*domain.Changelog, indent string) {
	for repoURL, cl := range subs {
		fmt.Printf("%ssubmodule changelog (%s): %d commits (prev=%.12s)\n",
			indent, repoURL, len(cl.Commits), cl.PreviousVersion)
		for i, commit := range cl.Commits {
			if i >= 3 {
				fmt.Printf("%s  ... and %d more\n", indent, len(cl.Commits)-3)
				break
			}
			fmt.Printf("%s  %.12s  %s\n", indent, commit.Sha, commit.Message)
		}
		if len(cl.SubmoduleChangelogs) > 0 {
			printSubmoduleChangelogs(cl.SubmoduleChangelogs, indent+"  ")
		}
	}
}
