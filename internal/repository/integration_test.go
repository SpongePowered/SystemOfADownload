package repository_test

import (
	"encoding/json"
	"testing"
	"time"

	"github.com/google/go-cmp/cmp"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
	"github.com/testcontainers/testcontainers-go/wait"

	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/dbtypes"
	"github.com/spongepowered/systemofadownload/internal/repository"
	"github.com/spongepowered/systemofadownload/internal/testutil"
)

// setupTestRepo spins up a real Postgres so the jsonb_agg/jsonb_object_agg
// output is exercised by pgx's actual codec stack — exactly the path we
// hand-rolled into GetVersionDetailRaw via sqlc overrides + dbtypes.
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

	if _, err := pool.Exec(ctx, testutil.DBSchema(t)); err != nil {
		t.Fatalf("failed to apply schema: %v", err)
	}
	return repository.NewRepository(pool)
}

// seedVersion creates a group + artifact + version (+ assets/tags). All
// writes go through a single tx for speed.
func seedVersion(t *testing.T, repo repository.Repository, groupID, artifactID, version string, commitBody []byte, assets []db.CreateArtifactVersionAssetParams, tags map[string]string) {
	t.Helper()
	ctx := t.Context()

	err := repo.WithTx(ctx, func(tx repository.Tx) error {
		if _, err := tx.CreateGroup(ctx, db.CreateGroupParams{MavenID: groupID, Name: groupID}); err != nil {
			return err
		}
		artifact, err := tx.CreateArtifact(ctx, db.CreateArtifactParams{
			GroupID:         groupID,
			ArtifactID:      artifactID,
			Name:            artifactID,
			GitRepositories: []byte("[]"),
		})
		if err != nil {
			return err
		}
		av, err := tx.CreateArtifactVersion(ctx, db.CreateArtifactVersionParams{
			ArtifactID: artifact.ID,
			Version:    version,
			SortOrder:  1,
			CommitBody: commitBody,
		})
		if err != nil {
			return err
		}
		for _, a := range assets {
			a.ArtifactVersionID = av.ID
			if _, err := tx.CreateArtifactVersionAsset(ctx, a); err != nil {
				return err
			}
		}
		for k, v := range tags {
			if _, err := tx.CreateArtifactVersionTag(ctx, db.CreateArtifactVersionTagParams{
				ArtifactVersionID: av.ID,
				TagKey:            k,
				TagValue:          v,
			}); err != nil {
				return err
			}
		}
		return nil
	})
	if err != nil {
		t.Fatalf("seed failed: %v", err)
	}
}

func TestIntegration_GetVersionDetail_PopulatedAggregates(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}
	repo := setupTestRepo(t)

	commitBody := []byte(`{"sha":"deadbeef","message":"release 1.0.0"}`)
	assets := []db.CreateArtifactVersionAssetParams{
		{
			DownloadUrl: "https://example/spongevanilla-1.0.0.jar",
			Sha256:      new("aaaa"),
			Md5:         new("m1"),
			Sha1:        new("s1"),
			Sha512:      new("l1"),
			Extension:   new("jar"),
		},
		{
			Classifier:  new("sources"),
			DownloadUrl: "https://example/spongevanilla-1.0.0-sources.jar",
			Sha256:      new("bbbb"),
			Md5:         new("m2"),
			Sha1:        new("s2"),
			Sha512:      new("l2"),
			Extension:   new("jar"),
		},
	}
	tags := map[string]string{
		"minecraft": "1.21.5",
		"api":       "15",
	}
	seedVersion(t, repo, "org.spongepowered", "spongevanilla", "1.0.0", commitBody, assets, tags)

	got, err := repo.GetVersionDetail(t.Context(), "org.spongepowered", "spongevanilla", "1.0.0")
	if err != nil {
		t.Fatalf("GetVersionDetail: %v", err)
	}

	wantAssets := dbtypes.VersionAssets{
		{DownloadURL: "https://example/spongevanilla-1.0.0.jar", Md5: "m1", Sha1: "s1", Sha256: "aaaa", Sha512: "l1", Extension: "jar"},
		{Classifier: "sources", DownloadURL: "https://example/spongevanilla-1.0.0-sources.jar", Md5: "m2", Sha1: "s2", Sha256: "bbbb", Sha512: "l2", Extension: "jar"},
	}
	wantTags := dbtypes.VersionTagMap{"minecraft": "1.21.5", "api": "15"}

	if got.Version != "1.0.0" {
		t.Errorf("Version: got %q, want %q", got.Version, "1.0.0")
	}
	if got.Recommended {
		t.Errorf("Recommended: got true, want false (default)")
	}
	// jsonb re-serializes the input (adds whitespace etc.), so compare
	// semantically rather than byte-for-byte.
	var wantBody, gotBody any
	if err := json.Unmarshal(commitBody, &wantBody); err != nil {
		t.Fatalf("unmarshal want commit body: %v", err)
	}
	if err := json.Unmarshal(got.CommitBody, &gotBody); err != nil {
		t.Fatalf("unmarshal got commit body: %v", err)
	}
	if diff := cmp.Diff(wantBody, gotBody); diff != "" {
		t.Errorf("CommitBody mismatch (-want +got):\n%s", diff)
	}
	// jsonb_agg ordering is not guaranteed; sort both before compare by classifier.
	cmpAssets := func(a, b dbtypes.VersionAsset) bool { return a.Classifier < b.Classifier }
	if diff := cmp.Diff(wantAssets, got.Assets, cmpOptSortSlice(cmpAssets)); diff != "" {
		t.Errorf("Assets mismatch (-want +got):\n%s", diff)
	}
	if diff := cmp.Diff(wantTags, got.Tags); diff != "" {
		t.Errorf("Tags mismatch (-want +got):\n%s", diff)
	}
}

func TestIntegration_GetVersionDetail_EmptyAggregates(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}
	repo := setupTestRepo(t)

	// Version with zero assets and zero tags — exercises the COALESCE
	// '[]'::jsonb / '{}'::jsonb fallbacks all the way through pgx's codec
	// into dbtypes.VersionAssets / VersionTagMap.
	seedVersion(t, repo, "g", "a", "0.0.1", nil, nil, nil)

	got, err := repo.GetVersionDetail(t.Context(), "g", "a", "0.0.1")
	if err != nil {
		t.Fatalf("GetVersionDetail: %v", err)
	}
	if got.Assets == nil {
		t.Error("Assets: want non-nil empty slice (the COALESCE fallback), got nil")
	}
	if len(got.Assets) != 0 {
		t.Errorf("Assets len: want 0, got %d (%v)", len(got.Assets), got.Assets)
	}
	if got.Tags == nil {
		t.Error("Tags: want non-nil empty map (the COALESCE fallback), got nil")
	}
	if len(got.Tags) != 0 {
		t.Errorf("Tags len: want 0, got %d (%v)", len(got.Tags), got.Tags)
	}
}

func TestIntegration_GetVersionDetail_NotFound(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}
	repo := setupTestRepo(t)

	_, err := repo.GetVersionDetail(t.Context(), "missing", "missing", "0.0.0")
	if err == nil {
		t.Fatal("expected pgx.ErrNoRows, got nil")
	}
	// repository wraps nothing here — caller (service) is what translates to
	// the domain ErrVersionNotFound, so we assert on the raw pgx sentinel.
	if !isNoRows(err) {
		t.Errorf("want pgx.ErrNoRows, got %T: %v", err, err)
	}
}

func isNoRows(err error) bool {
	for ; err != nil; err = unwrap(err) {
		if err == pgx.ErrNoRows { //nolint:errorlint // direct sentinel check
			return true
		}
	}
	return false
}

func unwrap(err error) error {
	if u, ok := err.(interface{ Unwrap() error }); ok {
		return u.Unwrap()
	}
	return nil
}

// stand-in for cmpopts.SortSlices to avoid pulling in another dep just for
// this test; ordering of jsonb_agg results is not guaranteed in Postgres.
func cmpOptSortSlice[T any](less func(a, b T) bool) cmp.Option {
	return cmp.Transformer("sort", func(in []T) []T {
		out := append([]T(nil), in...)
		for i := 1; i < len(out); i++ {
			for j := i; j > 0 && less(out[j], out[j-1]); j-- {
				out[j-1], out[j] = out[j], out[j-1]
			}
		}
		return out
	})
}
