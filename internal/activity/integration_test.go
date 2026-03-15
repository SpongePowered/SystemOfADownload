package activity_test

import (
	"testing"
	"time"

	"github.com/google/go-cmp/cmp"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
	"github.com/testcontainers/testcontainers-go/wait"

	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/repository"
)

const schema = `
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
    git_repositories JSONB NOT NULL DEFAULT '[]',
    UNIQUE(group_id, artifact_id)
);

CREATE TABLE artifact_versions (
    id BIGSERIAL PRIMARY KEY,
    artifact_id BIGINT NOT NULL REFERENCES artifacts(id),
    version TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
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
`

func setupTestRepo(t *testing.T) repository.Repository {
	t.Helper()
	ctx := t.Context()

	pgContainer, err := postgres.Run(ctx,
		"postgres:16-alpine",
		postgres.WithDatabase("testdb"),
		postgres.WithUsername("testuser"),
		postgres.WithPassword("testpass"),
		testcontainers.WithWaitStrategy(
			wait.ForLog("database system is ready to accept connections").
				WithOccurrence(2).
				WithStartupTimeout(30*time.Second),
		),
	)
	if err != nil {
		t.Fatalf("failed to start postgres container: %v", err)
	}
	t.Cleanup(func() {
		if err := pgContainer.Terminate(ctx); err != nil {
			t.Logf("failed to terminate postgres container: %v", err)
		}
	})

	connStr, err := pgContainer.ConnectionString(ctx, "sslmode=disable")
	if err != nil {
		t.Fatalf("failed to get connection string: %v", err)
	}

	pool, err := pgxpool.New(ctx, connStr)
	if err != nil {
		t.Fatalf("failed to create connection pool: %v", err)
	}
	t.Cleanup(func() { pool.Close() })

	if _, err := pool.Exec(ctx, schema); err != nil {
		t.Fatalf("failed to apply schema: %v", err)
	}

	return repository.NewRepository(pool)
}

func seedGroupAndArtifact(t *testing.T, repo repository.Repository, groupID, artifactID string) {
	t.Helper()
	ctx := t.Context()

	err := repo.WithTx(ctx, func(tx repository.Tx) error {
		_, err := tx.CreateGroup(ctx, db.CreateGroupParams{
			MavenID: groupID,
			Name:    groupID,
		})
		if err != nil {
			return err
		}
		_, err = tx.CreateArtifact(ctx, db.CreateArtifactParams{
			GroupID:         groupID,
			ArtifactID:      artifactID,
			Name:            artifactID,
			GitRepositories: []byte("[]"),
		})
		return err
	})
	if err != nil {
		t.Fatalf("failed to seed group and artifact: %v", err)
	}
}

func TestIntegration_StoreNewVersions(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}

	const (
		groupID    = "org.spongepowered"
		artifactID = "spongeapi"
	)

	repo := setupTestRepo(t)
	seedGroupAndArtifact(t, repo, groupID, artifactID)

	act := &activity.VersionSyncActivities{Repo: repo}

	t.Run("stores new versions and returns them", func(t *testing.T) {
		input := activity.StoreNewVersionsInput{
			GroupID:    groupID,
			ArtifactID: artifactID,
			Versions: []domain.VersionInfo{
				{GroupID: groupID, ArtifactID: artifactID, Version: "8.0.0"},
				{GroupID: groupID, ArtifactID: artifactID, Version: "7.4.0"},
				{GroupID: groupID, ArtifactID: artifactID, Version: "7.3.0"},
			},
		}

		got, err := act.StoreNewVersions(t.Context(), input)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}

		want := &activity.StoreNewVersionsOutput{
			NewVersions: []domain.VersionInfo{
				{GroupID: groupID, ArtifactID: artifactID, Version: "8.0.0"},
				{GroupID: groupID, ArtifactID: artifactID, Version: "7.4.0"},
				{GroupID: groupID, ArtifactID: artifactID, Version: "7.3.0"},
			},
		}
		if diff := cmp.Diff(want, got); diff != "" {
			t.Fatalf("output diff (-want +got):\n%s", diff)
		}

		// Verify versions are actually in the DB.
		versions, err := repo.ListArtifactVersions(t.Context(), db.ListArtifactVersionsParams{
			GroupID:    groupID,
			ArtifactID: artifactID,
		})
		if err != nil {
			t.Fatalf("failed to list versions: %v", err)
		}
		if len(versions) != 3 {
			t.Fatalf("expected 3 versions in DB, got %d", len(versions))
		}
	})

	t.Run("second call with overlap only returns new versions", func(t *testing.T) {
		input := activity.StoreNewVersionsInput{
			GroupID:    groupID,
			ArtifactID: artifactID,
			Versions: []domain.VersionInfo{
				{GroupID: groupID, ArtifactID: artifactID, Version: "8.0.0"},
				{GroupID: groupID, ArtifactID: artifactID, Version: "9.0.0"},
			},
		}

		got, err := act.StoreNewVersions(t.Context(), input)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}

		want := &activity.StoreNewVersionsOutput{
			NewVersions: []domain.VersionInfo{
				{GroupID: groupID, ArtifactID: artifactID, Version: "9.0.0"},
			},
		}
		if diff := cmp.Diff(want, got); diff != "" {
			t.Fatalf("output diff (-want +got):\n%s", diff)
		}
	})

	t.Run("call with all existing versions returns empty", func(t *testing.T) {
		input := activity.StoreNewVersionsInput{
			GroupID:    groupID,
			ArtifactID: artifactID,
			Versions: []domain.VersionInfo{
				{GroupID: groupID, ArtifactID: artifactID, Version: "8.0.0"},
				{GroupID: groupID, ArtifactID: artifactID, Version: "7.4.0"},
			},
		}

		got, err := act.StoreNewVersions(t.Context(), input)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}

		want := &activity.StoreNewVersionsOutput{}
		if diff := cmp.Diff(want, got); diff != "" {
			t.Fatalf("output diff (-want +got):\n%s", diff)
		}
	})

	t.Run("empty input returns immediately", func(t *testing.T) {
		input := activity.StoreNewVersionsInput{
			GroupID:    groupID,
			ArtifactID: artifactID,
			Versions:   nil,
		}

		got, err := act.StoreNewVersions(t.Context(), input)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}

		want := &activity.StoreNewVersionsOutput{}
		if diff := cmp.Diff(want, got); diff != "" {
			t.Fatalf("output diff (-want +got):\n%s", diff)
		}
	})

	t.Run("nonexistent artifact returns error", func(t *testing.T) {
		input := activity.StoreNewVersionsInput{
			GroupID:    groupID,
			ArtifactID: "nonexistent",
			Versions: []domain.VersionInfo{
				{GroupID: groupID, ArtifactID: "nonexistent", Version: "1.0.0"},
			},
		}

		_, err := act.StoreNewVersions(t.Context(), input)
		if err == nil {
			t.Fatal("expected error for nonexistent artifact, got nil")
		}
	})
}
