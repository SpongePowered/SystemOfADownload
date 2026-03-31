// Command devtest seeds a local database with real SpongeVanilla and SpongeForge
// version data (fetched from Sonatype), registers a Temporal worker, and
// executes VersionOrderingWorkflow for both artifacts so the event history
// can be inspected in the Temporal UI (http://localhost:8233).
//
// Prerequisites:
//   - Temporal server running locally (localhost:7233)
//   - Docker running (for testcontainers PostgreSQL)
//
// Usage:
//
//	go run ./cmd/devtest
package main

import (
	"context"
	"encoding/json"
	"encoding/xml"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
	"github.com/testcontainers/testcontainers-go/wait"
	"go.temporal.io/sdk/client"
	"go.temporal.io/sdk/worker"

	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/app"
	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/repository"
	wf "github.com/spongepowered/systemofadownload/internal/workflow"
)

const (
	groupID      = "org.spongepowered"
	sonatypeBase = "https://repo.spongepowered.org"
	repoName     = "maven-public"
	taskQueue    = "version-sync"
	temporalAddr = "localhost:7233"
)

// mcPattern is the regex alternation for all Minecraft version formats.
const mcPattern = `(?:\d+w\d+[a-z]|\d+\.\d+(?:\.\d+)?-snapshot-\d+|\d+\.\d+(?:\.\d+)?-(?:pre|rc)-?\d+|\d+\.\d+(?:\.\d+)?)`

type artifactConfig struct {
	ArtifactID string
	Schema     *domain.VersionSchema
}

var artifacts = []artifactConfig{
	{
		ArtifactID: "spongevanilla",
		Schema: &domain.VersionSchema{
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
					// 1.12.2-7.1.0-BETA-2975
					Name:    "beta-era",
					Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<api>\d+\.\d+\.\d+)-(?P<qualifier>BETA)-(?P<build>\d+)$`,
					Segments: []domain.SegmentRule{
						{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
						{Name: "api", ParseAs: "dotted", TagKey: "api"},
					},
				},
				{
					// 1.15.2-8.0.0-SNAPSHOT
					Name:    "snapshot",
					Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<api>\d+\.\d+\.\d+)-(?P<qualifier>SNAPSHOT)$`,
					Segments: []domain.SegmentRule{
						{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
						{Name: "api", ParseAs: "dotted", TagKey: "api"},
					},
				},
				{
					// 1.8-2.1-DEV-128
					Name:    "dev-era",
					Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<api>\d+\.\d+(?:\.\d+)?)-(?P<qualifier>DEV)-(?P<build>\d+)$`,
					Segments: []domain.SegmentRule{
						{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
						{Name: "api", ParseAs: "dotted", TagKey: "api"},
					},
				},
				{
					// 1.8-2.1DEV-19 (no dash before DEV)
					Name:    "dev-era-compact",
					Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<api>\d+\.\d+(?:\.\d+)?)(?P<qualifier>DEV)-(?P<build>\d+)$`,
					Segments: []domain.SegmentRule{
						{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
						{Name: "api", ParseAs: "dotted", TagKey: "api"},
					},
				},
				{
					// 1.10.2-bleeding-BETA-148
					Name:    "bleeding-beta",
					Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-bleeding-(?P<qualifier>BETA)-(?P<build>\d+)$`,
					Segments: []domain.SegmentRule{
						{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
					},
				},
				{
					// 1.9.4-bleeding-1.10-BETA-78 (bleeding with intermediate version)
					Name:    "bleeding-beta-intermediate",
					Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-bleeding-[\d.]+-(?P<qualifier>BETA)-(?P<build>\d+)$`,
					Segments: []domain.SegmentRule{
						{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
					},
				},
			},
		},
	},
	{
		ArtifactID: "spongeforge",
		Schema: &domain.VersionSchema{
			UseMojangManifest: true,
			Variants: []domain.VersionFormatVariant{
				{
					Name:    "current",
					Pattern: `^(?P<minecraft>` + mcPattern + `)-(?P<forge>\d+(?:\.\d+)*)-(?P<api>\d+\.\d+\.\d+)(?:-(?P<qualifier>RC)(?P<build>\d+))?$`,
					Segments: []domain.SegmentRule{
						{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
						{Name: "forge", ParseAs: "dotted", TagKey: "forge"},
						{Name: "api", ParseAs: "dotted", TagKey: "api"},
					},
				},
				{
					// 1.12.2-2838-7.1.0-BETA-2975
					Name:    "beta-era",
					Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<forge>\d+)-(?P<api>\d+\.\d+\.\d+)-(?P<qualifier>BETA)-(?P<build>\d+)$`,
					Segments: []domain.SegmentRule{
						{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
						{Name: "forge", ParseAs: "integer", TagKey: "forge"},
						{Name: "api", ParseAs: "dotted", TagKey: "api"},
					},
				},
				{
					// 1.12.2-1.12.2-14.23.5.2768-7.1.6-RC3582 (duplicated MC prefix)
					Name:    "duplicate-mc",
					Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-\d+\.\d+(?:\.\d+)?-(?P<forge>\d+(?:\.\d+)*)-(?P<api>\d+\.\d+\.\d+)(?:-(?P<qualifier>RC)(?P<build>\d+))?$`,
					Segments: []domain.SegmentRule{
						{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
						{Name: "forge", ParseAs: "dotted", TagKey: "forge"},
						{Name: "api", ParseAs: "dotted", TagKey: "api"},
					},
				},
				{
					// 1.8-1521-2.1-DEV-729
					Name:    "dev-era",
					Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<forge>\d+)-(?P<api>\d+\.\d+(?:\.\d+)?)-(?P<qualifier>DEV)-(?P<build>\d+)$`,
					Segments: []domain.SegmentRule{
						{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
						{Name: "forge", ParseAs: "integer", TagKey: "forge"},
						{Name: "api", ParseAs: "dotted", TagKey: "api"},
					},
				},
				{
					// 1.8-1521-2.1DEV-728 (no dash before DEV)
					Name:    "dev-era-compact",
					Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<forge>\d+)-(?P<api>\d+\.\d+(?:\.\d+)?)(?P<qualifier>DEV)-(?P<build>\d+)$`,
					Segments: []domain.SegmentRule{
						{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
						{Name: "forge", ParseAs: "integer", TagKey: "forge"},
						{Name: "api", ParseAs: "dotted", TagKey: "api"},
					},
				},
				{
					// 1.10.2-2098-bleeding-BETA-1816
					Name:    "bleeding-beta",
					Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<forge>\d+)-bleeding-(?P<qualifier>BETA)-(?P<build>\d+)$`,
					Segments: []domain.SegmentRule{
						{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
						{Name: "forge", ParseAs: "integer", TagKey: "forge"},
					},
				},
				{
					// 1.10-1983-bleeding-1.10-BETA-1505 (bleeding with intermediate version)
					Name:    "bleeding-beta-intermediate",
					Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<forge>\d+)-bleeding-[\d.]+-(?P<qualifier>BETA)-(?P<build>\d+)$`,
					Segments: []domain.SegmentRule{
						{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
						{Name: "forge", ParseAs: "integer", TagKey: "forge"},
					},
				},
			},
		},
	},
}

const pgSchema = `
CREATE TABLE groups (
    maven_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    website TEXT
);
CREATE TABLE artifacts (
    id BIGSERIAL PRIMARY KEY,
    group_id TEXT NOT NULL REFERENCES groups(maven_id),
    artifact_id TEXT NOT NULL,
    name TEXT NOT NULL,
    website TEXT,
    issues TEXT,
    git_repositories JSONB NOT NULL DEFAULT '[]',
    version_schema JSONB,
    UNIQUE(group_id, artifact_id)
);
CREATE TABLE artifact_versions (
    id BIGSERIAL PRIMARY KEY,
    artifact_id BIGINT NOT NULL REFERENCES artifacts(id),
    version TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    recommended BOOLEAN NOT NULL DEFAULT false,
    commit_body JSONB,
    UNIQUE(artifact_id, version)
);
CREATE TABLE artifact_versioned_assets (
    id BIGSERIAL PRIMARY KEY,
    artifact_version_id BIGINT NOT NULL REFERENCES artifact_versions(id),
    classifier TEXT,
    sha256 TEXT,
    download_url TEXT NOT NULL
);
CREATE TABLE artifact_versioned_tags (
    artifact_version_id BIGINT NOT NULL REFERENCES artifact_versions(id),
    tag_key TEXT NOT NULL,
    tag_value TEXT NOT NULL,
    PRIMARY KEY (artifact_version_id, tag_key)
);

CREATE INDEX idx_versioned_tags_key_value ON artifact_versioned_tags(tag_key, tag_value, artifact_version_id);
CREATE INDEX idx_versions_artifact_sort ON artifact_versions(artifact_id, sort_order DESC);
CREATE INDEX idx_versions_artifact_recommended_sort ON artifact_versions(artifact_id, recommended, sort_order DESC);
`

func main() {
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

	if _, err := pool.Exec(ctx, pgSchema); err != nil {
		return fmt.Errorf("applying schema: %w", err)
	}
	slog.InfoContext(ctx, "PostgreSQL ready")

	repo := repository.NewRepository(pool)

	// --- Seed data ---
	slog.InfoContext(ctx, "seeding group")
	if err := repo.WithTx(ctx, func(tx repository.Tx) error {
		_, err := tx.CreateGroup(ctx, db.CreateGroupParams{
			MavenID: groupID,
			Name:    "SpongePowered",
		})
		return err
	}); err != nil {
		return fmt.Errorf("creating group: %w", err)
	}

	for _, ac := range artifacts {
		if err := seedArtifact(ctx, repo, ac); err != nil {
			return fmt.Errorf("seeding %s: %w", ac.ArtifactID, err)
		}
	}

	// --- Connect to Temporal ---
	slog.InfoContext(ctx, "connecting to Temporal", "addr", temporalAddr)
	c, err := client.Dial(client.Options{HostPort: temporalAddr})
	if err != nil {
		return fmt.Errorf("dialing temporal: %w", err)
	}
	defer c.Close()

	// --- Register worker ---
	w := worker.New(c, taskQueue, worker.Options{})
	w.RegisterWorkflow(wf.VersionOrderingWorkflow)
	w.RegisterActivity(activity.NewVersionOrderingActivities(repo))

	if err := w.Start(); err != nil {
		return fmt.Errorf("starting worker: %w", err)
	}
	defer w.Stop()
	slog.InfoContext(ctx, "worker registered and started", "taskQueue", taskQueue)

	// --- Execute workflows ---
	for _, ac := range artifacts {
		wfID := fmt.Sprintf("devtest-ordering-%s", ac.ArtifactID)
		slog.InfoContext(ctx, "starting VersionOrderingWorkflow",
			"artifactID", ac.ArtifactID, "workflowID", wfID)

		run, err := c.ExecuteWorkflow(ctx, client.StartWorkflowOptions{
			ID:        wfID,
			TaskQueue: taskQueue,
		}, wf.VersionOrderingWorkflow, wf.VersionOrderingInput{
			GroupID:    groupID,
			ArtifactID: ac.ArtifactID,
		})
		if err != nil {
			return fmt.Errorf("starting workflow for %s: %w", ac.ArtifactID, err)
		}

		var result wf.VersionOrderingOutput
		if err := run.Get(ctx, &result); err != nil {
			return fmt.Errorf("workflow %s failed: %w", ac.ArtifactID, err)
		}
		slog.InfoContext(ctx, "workflow completed",
			"artifactID", ac.ArtifactID, "versionsOrdered", result.VersionsOrdered)
	}

	// --- Verify: print sample tags per artifact ---
	fmt.Println()
	for _, ac := range artifacts {
		if err := printSampleTags(ctx, repo, ac.ArtifactID); err != nil {
			slog.WarnContext(ctx, "failed to print sample tags",
				"artifactID", ac.ArtifactID, "error", err)
		}
	}

	// --- Verify: GetArtifact service call ---
	fmt.Println()
	svc := app.NewService(repo)
	for _, ac := range artifacts {
		artifact, tags, err := svc.GetArtifact(ctx, groupID, ac.ArtifactID)
		if err != nil {
			slog.WarnContext(ctx, "GetArtifact failed", "artifactID", ac.ArtifactID, "error", err)
			continue
		}
		slog.InfoContext(ctx, "verified GetArtifact",
			"artifactID", ac.ArtifactID,
			"displayName", artifact.DisplayName)
		for key, values := range tags {
			if len(values) > 5 {
				fmt.Printf("  tag %q: %d distinct values (first 5: %v)\n", key, len(values), values[:5])
			} else {
				fmt.Printf("  tag %q: %v\n", key, values)
			}
		}
	}

	// --- Verify: GetVersions service call ---
	fmt.Println()
	for _, ac := range artifacts {
		entries, err := svc.GetVersions(ctx, repository.VersionQueryParams{
			GroupID:    groupID,
			ArtifactID: ac.ArtifactID,
			Limit:      5,
			Offset:     0,
		})
		if err != nil {
			slog.WarnContext(ctx, "GetVersions failed", "artifactID", ac.ArtifactID, "error", err)
			continue
		}
		slog.InfoContext(ctx, "verified GetVersions",
			"artifactID", ac.ArtifactID, "returned", len(entries.Entries), "total", entries.Total)
		for _, e := range entries.Entries {
			fmt.Printf("  %-45s recommended=%-5t tags=%v\n", e.Version, e.Recommended, e.Tags)
		}

		// Also test with recommended=true
		rec := true
		recEntries, err := svc.GetVersions(ctx, repository.VersionQueryParams{
			GroupID:     groupID,
			ArtifactID:  ac.ArtifactID,
			Recommended: &rec,
			Limit:       5,
			Offset:      0,
		})
		if err != nil {
			slog.WarnContext(ctx, "GetVersions (recommended) failed", "artifactID", ac.ArtifactID, "error", err)
			continue
		}
		slog.InfoContext(ctx, "verified GetVersions recommended=true",
			"artifactID", ac.ArtifactID, "returned", len(recEntries.Entries), "total", recEntries.Total)
	}

	fmt.Println()
	slog.InfoContext(ctx, "all workflows completed — inspect event history at http://localhost:8233")
	slog.InfoContext(ctx, "press Ctrl+C to shut down")

	// Keep alive so the user can inspect.
	<-ctx.Done()
	return nil
}

func seedArtifact(ctx context.Context, repo repository.Repository, ac artifactConfig) error {
	// Fetch real versions from Sonatype.
	versions, err := fetchMavenVersions(ctx, ac.ArtifactID)
	if err != nil {
		return fmt.Errorf("fetching versions: %w", err)
	}
	slog.InfoContext(ctx, "fetched versions from Sonatype",
		"artifactID", ac.ArtifactID, "count", len(versions))

	// Marshal schema.
	var schemaJSON []byte
	if ac.Schema != nil {
		schemaJSON, err = json.Marshal(ac.Schema)
		if err != nil {
			return fmt.Errorf("marshaling schema: %w", err)
		}
	}

	// Create artifact.
	if err := repo.WithTx(ctx, func(tx repository.Tx) error {
		_, err := tx.CreateArtifact(ctx, db.CreateArtifactParams{
			GroupID:         groupID,
			ArtifactID:      ac.ArtifactID,
			Name:            ac.ArtifactID,
			GitRepositories: []byte("[]"),
			VersionSchema:   schemaJSON,
		})
		return err
	}); err != nil {
		return fmt.Errorf("creating artifact: %w", err)
	}

	// Look up artifact ID.
	artifact, err := repo.GetArtifactByGroupAndId(ctx, db.GetArtifactByGroupAndIdParams{
		GroupID:    groupID,
		ArtifactID: ac.ArtifactID,
	})
	if err != nil {
		return fmt.Errorf("looking up artifact: %w", err)
	}

	// Seed versions in batches of 500 to keep transactions small.
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
			return fmt.Errorf("seeding versions batch %d-%d: %w", i, end, err)
		}
	}
	slog.InfoContext(ctx, "seeded versions", "artifactID", ac.ArtifactID, "count", len(versions))
	return nil
}

type mavenMetadata struct {
	Versioning struct {
		Versions struct {
			Version []string `xml:"version"`
		} `xml:"versions"`
	} `xml:"versioning"`
}

func fetchMavenVersions(ctx context.Context, artifactID string) ([]string, error) {
	groupPath := strings.ReplaceAll(groupID, ".", "/")
	url := fmt.Sprintf("%s/repository/%s/%s/%s/maven-metadata.xml",
		sonatypeBase, repoName, groupPath, artifactID)

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

func printSampleTags(ctx context.Context, repo repository.Repository, artifactID string) error {
	// Grab some versions across the sort order range.
	versions, err := repo.ListArtifactVersions(ctx, db.ListArtifactVersionsParams{
		GroupID:    groupID,
		ArtifactID: artifactID,
	})
	if err != nil {
		return err
	}
	if len(versions) == 0 {
		slog.InfoContext(ctx, "no versions found", "artifactID", artifactID)
		return nil
	}

	// Pick a few samples: newest 3, oldest 3, and 3 from the middle.
	samples := pickSamples(versions, 3)

	fmt.Printf("[%s] Sample version tags (%d total versions):\n", artifactID, len(versions))
	for _, v := range samples {
		tags, err := repo.ListArtifactVersionTags(ctx, v.ID)
		if err != nil {
			return fmt.Errorf("listing tags for version %d: %w", v.ID, err)
		}
		tagMap := make(map[string]string, len(tags))
		for _, t := range tags {
			tagMap[t.TagKey] = t.TagValue
		}
		fmt.Printf("  sort=%04d  %-45s  tags=%v\n", v.SortOrder, v.Version, tagMap)
	}

	// Also count how many versions have no tags (unmatched by schema).
	var untagged int
	for _, v := range versions {
		tags, _ := repo.ListArtifactVersionTags(ctx, v.ID)
		if len(tags) == 0 {
			untagged++
		}
	}
	if untagged > 0 {
		slog.WarnContext(ctx, "versions unmatched by schema",
			"artifactID", artifactID, "untagged", untagged, "total", len(versions))
		count := 0
		for _, v := range versions {
			tags, _ := repo.ListArtifactVersionTags(ctx, v.ID)
			if len(tags) == 0 {
				fmt.Printf("    untagged: %s\n", v.Version)
				count++
				if count >= 5 {
					break
				}
			}
		}
	}
	return nil
}

// pickSamples returns up to n versions from the start, middle, and end of the slice.
func pickSamples(versions []db.ArtifactVersion, n int) []db.ArtifactVersion {
	if len(versions) <= n*3 {
		return versions
	}
	var result []db.ArtifactVersion
	// Newest (list is sorted DESC by sort_order).
	for i := 0; i < n; i++ {
		result = append(result, versions[i])
	}
	// Middle.
	mid := len(versions) / 2
	for i := mid - n/2; i < mid-n/2+n; i++ {
		result = append(result, versions[i])
	}
	// Oldest.
	for i := len(versions) - n; i < len(versions); i++ {
		result = append(result, versions[i])
	}
	return result
}
