package activity_test

import (
	"context"
	"encoding/json"
	"errors"
	"strings"
	"testing"

	"github.com/stretchr/testify/mock"

	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/repository"
	repomocks "github.com/spongepowered/systemofadownload/internal/repository/mocks"
)

type fakeGitHubAuthorResolver struct {
	username string
	err      error
	calls    int
}

func (f *fakeGitHubAuthorResolver) ResolveUsername(
	context.Context,
	string,
	string,
	string,
) (string, error) {
	f.calls++
	return f.username, f.err
}

func TestFetchVersionsNeedingAuthorResolution(t *testing.T) {
	t.Parallel()

	repo := repomocks.NewMockRepository(t)
	beforeID := int64(100)
	repo.EXPECT().ListVersionsNeedingGitHubAuthorResolution(
		t.Context(),
		db.ListVersionsNeedingGitHubAuthorResolutionParams{
			BeforeID: &beforeID,
			PageSize: 50,
		},
	).Return([]int64{90, 80}, nil)

	activities := &activity.GitHubAuthorResolutionActivities{Repo: repo}
	got, err := activities.FetchVersionsNeedingAuthorResolution(
		t.Context(),
		activity.FetchVersionsNeedingAuthorResolutionInput{
			BeforeID: &beforeID,
			PageSize: 50,
		},
	)
	if err != nil {
		t.Fatalf("FetchVersionsNeedingAuthorResolution() error = %v", err)
	}
	if len(got) != 2 || got[len(got)-1] != 80 {
		t.Fatalf("FetchVersionsNeedingAuthorResolution() = %#v", got)
	}
}

func TestResolveGitHubAuthorsBatchUsesPersistentCacheAndLocksVersion(t *testing.T) {
	t.Parallel()

	cachedUsername := "cached-user"
	info := domain.CommitInfo{
		Sha:        "head-sha",
		Repository: "https://github.com/SpongePowered/Sponge",
		EnrichedAt: "2026-07-27T00:00:00Z",
		Author: &domain.CommitAuthor{
			Name:  "Cached Author",
			Email: "cached@example.com",
		},
		Changelog: &domain.Changelog{
			Commits: []domain.CommitSummary{{
				Sha: "head-sha",
				Author: &domain.CommitAuthor{
					Name:  "Cached Author",
					Email: "cached@example.com",
				},
			}},
			SubmoduleChangelogs: map[string]*domain.Changelog{
				"https://github.com/SpongePowered/SpongeAPI": {
					Commits: []domain.CommitSummary{{
						Sha: "sub-sha",
						Author: &domain.CommitAuthor{
							Name:  "Noreply Author",
							Email: "123+noreply-user@users.noreply.github.com",
						},
					}},
				},
			},
		},
	}
	body, err := json.Marshal(info)
	if err != nil {
		t.Fatal(err)
	}

	repo := repomocks.NewMockRepository(t)
	tx := repomocks.NewMockTx(t)
	resolver := &fakeGitHubAuthorResolver{}
	repo.EXPECT().GetArtifactVersionByID(mock.Anything, int64(10)).
		Return(db.ArtifactVersion{ID: 10, CommitBody: body}, nil)
	repo.EXPECT().GetGitHubUserCacheBatch(mock.Anything, []string{"cached@example.com"}).
		Return([]db.GithubUserCache{{
			AuthorEmail:    "cached@example.com",
			GithubUsername: &cachedUsername,
		}}, nil)
	repo.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
		func(ctx context.Context, fn func(repository.Tx) error) error {
			return fn(tx)
		},
	)
	tx.EXPECT().GetArtifactVersionForUpdate(mock.Anything, int64(10)).
		Return(db.ArtifactVersion{ID: 10, CommitBody: body}, nil)
	tx.EXPECT().UpdateArtifactVersionCommitBody(mock.Anything, mock.MatchedBy(
		func(params db.UpdateArtifactVersionCommitBodyParams) bool {
			var updated domain.CommitInfo
			if json.Unmarshal(params.CommitBody, &updated) != nil {
				return false
			}
			nested := updated.Changelog.SubmoduleChangelogs["https://github.com/SpongePowered/SpongeAPI"]
			return updated.Author.GitHubUsername == "cached-user" &&
				updated.Changelog.Commits[0].Author.GitHubUsername == "cached-user" &&
				nested.Commits[0].Author.GitHubUsername == "noreply-user" &&
				updated.AuthorResolution != nil &&
				updated.AuthorResolution.Schema == domain.AuthorResolutionSchema
		},
	)).Return(nil)

	activities := &activity.GitHubAuthorResolutionActivities{
		Repo:   repo,
		GitHub: resolver,
	}
	env := newActivityEnv(t)
	env.RegisterActivity(activities.ResolveGitHubAuthorsBatch)
	_, err = env.ExecuteActivity(
		activities.ResolveGitHubAuthorsBatch,
		activity.ResolveGitHubAuthorsBatchInput{VersionIDs: []int64{10}},
	)
	if err != nil {
		t.Fatalf("ResolveGitHubAuthorsBatch() error = %v", err)
	}
	if resolver.calls != 0 {
		t.Errorf("GitHub API calls = %d, want 0", resolver.calls)
	}
}

func TestResolveGitHubAuthorsBatchUsesGitHubOnCacheMiss(t *testing.T) {
	t.Parallel()

	info := domain.CommitInfo{
		Sha:        "head-sha",
		Repository: "https://github.com/SpongePowered/Sponge",
		EnrichedAt: "2026-07-27T00:00:00Z",
		Author: &domain.CommitAuthor{
			Name:  "Git Author",
			Email: "author@example.com",
		},
	}
	body, _ := json.Marshal(info)

	repo := repomocks.NewMockRepository(t)
	tx := repomocks.NewMockTx(t)
	resolver := &fakeGitHubAuthorResolver{username: "octocat"}
	repo.EXPECT().GetArtifactVersionByID(mock.Anything, int64(10)).
		Return(db.ArtifactVersion{ID: 10, CommitBody: body}, nil)
	repo.EXPECT().GetGitHubUserCacheBatch(mock.Anything, []string{"author@example.com"}).
		Return(nil, nil)
	repo.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
		func(ctx context.Context, fn func(repository.Tx) error) error {
			return fn(tx)
		},
	).Twice()
	tx.EXPECT().UpsertGitHubUserCache(mock.Anything, mock.MatchedBy(
		func(params db.UpsertGitHubUserCacheParams) bool {
			return params.AuthorEmail == "author@example.com" &&
				params.GithubUsername != nil &&
				*params.GithubUsername == "octocat"
		},
	)).Return(db.GithubUserCache{}, nil)
	tx.EXPECT().GetArtifactVersionForUpdate(mock.Anything, int64(10)).
		Return(db.ArtifactVersion{ID: 10, CommitBody: body}, nil)
	tx.EXPECT().UpdateArtifactVersionCommitBody(mock.Anything, mock.Anything).Return(nil)

	activities := &activity.GitHubAuthorResolutionActivities{
		Repo:   repo,
		GitHub: resolver,
	}
	env := newActivityEnv(t)
	env.RegisterActivity(activities.ResolveGitHubAuthorsBatch)
	_, err := env.ExecuteActivity(
		activities.ResolveGitHubAuthorsBatch,
		activity.ResolveGitHubAuthorsBatchInput{VersionIDs: []int64{10}},
	)
	if err != nil {
		t.Fatalf("ResolveGitHubAuthorsBatch() error = %v", err)
	}
	if resolver.calls != 1 {
		t.Errorf("GitHub API calls = %d, want 1", resolver.calls)
	}
}

func TestResolveGitHubAuthorsBatchSkipsUnresolvableVersion(t *testing.T) {
	t.Parallel()

	body, err := json.Marshal(domain.CommitInfo{EnrichedAt: "2026-07-27T00:00:00Z"})
	if err != nil {
		t.Fatal(err)
	}

	repo := repomocks.NewMockRepository(t)
	tx := repomocks.NewMockTx(t)
	// Version 10 has a commit body that no longer parses; it must be skipped
	// without stamping the marker so the batch reaches version 20.
	repo.EXPECT().GetArtifactVersionByID(mock.Anything, int64(10)).
		Return(db.ArtifactVersion{ID: 10, CommitBody: []byte("{not json")}, nil)
	repo.EXPECT().GetArtifactVersionByID(mock.Anything, int64(20)).
		Return(db.ArtifactVersion{ID: 20, CommitBody: body}, nil)
	repo.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
		func(ctx context.Context, fn func(repository.Tx) error) error {
			return fn(tx)
		},
	).Once()
	tx.EXPECT().GetArtifactVersionForUpdate(mock.Anything, int64(20)).
		Return(db.ArtifactVersion{ID: 20, CommitBody: body}, nil)
	tx.EXPECT().UpdateArtifactVersionCommitBody(mock.Anything, mock.Anything).Return(nil)

	activities := &activity.GitHubAuthorResolutionActivities{Repo: repo}
	env := newActivityEnv(t)
	env.RegisterActivity(activities.ResolveGitHubAuthorsBatch)
	if _, err := env.ExecuteActivity(
		activities.ResolveGitHubAuthorsBatch,
		activity.ResolveGitHubAuthorsBatchInput{VersionIDs: []int64{10, 20}},
	); err != nil {
		t.Fatalf("ResolveGitHubAuthorsBatch() error = %v", err)
	}
}

func TestResolveGitHubAuthorsBatchRetriesTransientFailure(t *testing.T) {
	t.Parallel()

	body, err := json.Marshal(domain.CommitInfo{
		Sha:        "head-sha",
		Repository: "https://github.com/SpongePowered/Sponge",
		EnrichedAt: "2026-07-27T00:00:00Z",
		Author:     &domain.CommitAuthor{Name: "Git Author", Email: "author@example.com"},
	})
	if err != nil {
		t.Fatal(err)
	}

	repo := repomocks.NewMockRepository(t)
	resolver := &fakeGitHubAuthorResolver{err: errors.New("github unavailable")}
	repo.EXPECT().GetArtifactVersionByID(mock.Anything, int64(10)).
		Return(db.ArtifactVersion{ID: 10, CommitBody: body}, nil)
	repo.EXPECT().GetGitHubUserCacheBatch(mock.Anything, []string{"author@example.com"}).
		Return(nil, nil)

	activities := &activity.GitHubAuthorResolutionActivities{Repo: repo, GitHub: resolver}
	env := newActivityEnv(t)
	env.RegisterActivity(activities.ResolveGitHubAuthorsBatch)
	_, err = env.ExecuteActivity(
		activities.ResolveGitHubAuthorsBatch,
		activity.ResolveGitHubAuthorsBatchInput{VersionIDs: []int64{10}},
	)
	if err == nil {
		t.Fatal("ResolveGitHubAuthorsBatch() error = nil, want retryable failure")
	}
	if !strings.Contains(err.Error(), "github unavailable") {
		t.Fatalf("ResolveGitHubAuthorsBatch() error = %v", err)
	}
}

func TestResolveGitHubAuthorsBatchResumesFromHeartbeat(t *testing.T) {
	t.Parallel()

	// No authors, so the version needs no lookups but must still be stamped.
	body, err := json.Marshal(domain.CommitInfo{EnrichedAt: "2026-07-27T00:00:00Z"})
	if err != nil {
		t.Fatal(err)
	}

	repo := repomocks.NewMockRepository(t)
	tx := repomocks.NewMockTx(t)
	// Version 10 precedes the heartbeat index and must never be touched.
	repo.EXPECT().GetArtifactVersionByID(mock.Anything, int64(20)).
		Return(db.ArtifactVersion{ID: 20, CommitBody: body}, nil)
	repo.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
		func(ctx context.Context, fn func(repository.Tx) error) error {
			return fn(tx)
		},
	)
	tx.EXPECT().GetArtifactVersionForUpdate(mock.Anything, int64(20)).
		Return(db.ArtifactVersion{ID: 20, CommitBody: body}, nil)
	tx.EXPECT().UpdateArtifactVersionCommitBody(mock.Anything, mock.Anything).Return(nil)

	activities := &activity.GitHubAuthorResolutionActivities{Repo: repo}
	env := newActivityEnv(t)
	env.SetHeartbeatDetails(struct {
		NextIndex int
	}{NextIndex: 1})
	env.RegisterActivity(activities.ResolveGitHubAuthorsBatch)
	_, err = env.ExecuteActivity(
		activities.ResolveGitHubAuthorsBatch,
		activity.ResolveGitHubAuthorsBatchInput{VersionIDs: []int64{10, 20}},
	)
	if err != nil {
		t.Fatalf("ResolveGitHubAuthorsBatch() error = %v", err)
	}
}

func TestResolveGitHubAuthorsBatchSkipsVersionAlreadyAtCurrentSchema(t *testing.T) {
	t.Parallel()

	body, err := json.Marshal(domain.CommitInfo{
		EnrichedAt: "2026-07-27T00:00:00Z",
		AuthorResolution: &domain.AuthorResolution{
			At:     "2026-07-27T01:00:00Z",
			Schema: domain.AuthorResolutionSchema,
		},
	})
	if err != nil {
		t.Fatal(err)
	}

	repo := repomocks.NewMockRepository(t)
	repo.EXPECT().GetArtifactVersionByID(mock.Anything, int64(20)).
		Return(db.ArtifactVersion{ID: 20, CommitBody: body}, nil)

	activities := &activity.GitHubAuthorResolutionActivities{Repo: repo}
	env := newActivityEnv(t)
	env.RegisterActivity(activities.ResolveGitHubAuthorsBatch)
	// No WithTx expectation: an up-to-date marker must not be rewritten.
	if _, err := env.ExecuteActivity(
		activities.ResolveGitHubAuthorsBatch,
		activity.ResolveGitHubAuthorsBatchInput{VersionIDs: []int64{20}},
	); err != nil {
		t.Fatalf("ResolveGitHubAuthorsBatch() error = %v", err)
	}
}

// emaillessCommitBody is a version whose only author has no email, so its
// cache key is the lowercased commit key rather than an address.
func emaillessCommitBody(t *testing.T) []byte {
	t.Helper()
	body, err := json.Marshal(domain.CommitInfo{
		Sha:        "HEAD-SHA",
		Repository: "https://github.com/SpongePowered/Sponge",
		EnrichedAt: "2026-07-27T00:00:00Z",
		Author:     &domain.CommitAuthor{Name: "Git Author"},
	})
	if err != nil {
		t.Fatal(err)
	}
	return body
}

const emaillessCommitKey = "commit:https://github.com/spongepowered/sponge@head-sha"

func TestResolveGitHubAuthorsBatchCachesEmaillessAuthorByCommit(t *testing.T) {
	t.Parallel()

	body := emaillessCommitBody(t)
	repo := repomocks.NewMockRepository(t)
	tx := repomocks.NewMockTx(t)
	resolver := &fakeGitHubAuthorResolver{username: "octocat"}
	repo.EXPECT().GetArtifactVersionByID(mock.Anything, int64(10)).
		Return(db.ArtifactVersion{ID: 10, CommitBody: body}, nil)
	repo.EXPECT().GetGitHubUserCacheBatch(mock.Anything, []string{emaillessCommitKey}).
		Return(nil, nil)
	repo.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
		func(ctx context.Context, fn func(repository.Tx) error) error {
			return fn(tx)
		},
	).Twice()
	tx.EXPECT().UpsertGitHubUserCache(mock.Anything, mock.MatchedBy(
		func(params db.UpsertGitHubUserCacheParams) bool {
			return params.AuthorEmail == emaillessCommitKey &&
				params.GithubUsername != nil &&
				*params.GithubUsername == "octocat"
		},
	)).Return(db.GithubUserCache{}, nil)
	tx.EXPECT().GetArtifactVersionForUpdate(mock.Anything, int64(10)).
		Return(db.ArtifactVersion{ID: 10, CommitBody: body}, nil)
	tx.EXPECT().UpdateArtifactVersionCommitBody(mock.Anything, mock.Anything).Return(nil)

	activities := &activity.GitHubAuthorResolutionActivities{
		Repo:   repo,
		GitHub: resolver,
	}
	env := newActivityEnv(t)
	env.RegisterActivity(activities.ResolveGitHubAuthorsBatch)
	if _, err := env.ExecuteActivity(
		activities.ResolveGitHubAuthorsBatch,
		activity.ResolveGitHubAuthorsBatchInput{VersionIDs: []int64{10}},
	); err != nil {
		t.Fatalf("ResolveGitHubAuthorsBatch() error = %v", err)
	}
	if resolver.calls != 1 {
		t.Errorf("GitHub API calls = %d, want 1", resolver.calls)
	}
}

func TestResolveGitHubAuthorsBatchServesEmaillessAuthorFromCache(t *testing.T) {
	t.Parallel()

	body := emaillessCommitBody(t)
	cachedUsername := "octocat"
	repo := repomocks.NewMockRepository(t)
	tx := repomocks.NewMockTx(t)
	resolver := &fakeGitHubAuthorResolver{}
	repo.EXPECT().GetArtifactVersionByID(mock.Anything, int64(10)).
		Return(db.ArtifactVersion{ID: 10, CommitBody: body}, nil)
	repo.EXPECT().GetGitHubUserCacheBatch(mock.Anything, []string{emaillessCommitKey}).
		Return([]db.GithubUserCache{{
			AuthorEmail:    emaillessCommitKey,
			GithubUsername: &cachedUsername,
		}}, nil)
	// One WithTx only: the merge. A cache hit must not upsert.
	repo.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
		func(ctx context.Context, fn func(repository.Tx) error) error {
			return fn(tx)
		},
	).Once()
	tx.EXPECT().GetArtifactVersionForUpdate(mock.Anything, int64(10)).
		Return(db.ArtifactVersion{ID: 10, CommitBody: body}, nil)
	tx.EXPECT().UpdateArtifactVersionCommitBody(mock.Anything, mock.MatchedBy(
		func(params db.UpdateArtifactVersionCommitBodyParams) bool {
			var info domain.CommitInfo
			if err := json.Unmarshal(params.CommitBody, &info); err != nil {
				return false
			}
			return info.Author != nil && info.Author.GitHubUsername == "octocat"
		},
	)).Return(nil)

	activities := &activity.GitHubAuthorResolutionActivities{
		Repo:   repo,
		GitHub: resolver,
	}
	env := newActivityEnv(t)
	env.RegisterActivity(activities.ResolveGitHubAuthorsBatch)
	if _, err := env.ExecuteActivity(
		activities.ResolveGitHubAuthorsBatch,
		activity.ResolveGitHubAuthorsBatchInput{VersionIDs: []int64{10}},
	); err != nil {
		t.Fatalf("ResolveGitHubAuthorsBatch() error = %v", err)
	}
	if resolver.calls != 0 {
		t.Errorf("GitHub API calls = %d, want 0 (served from cache)", resolver.calls)
	}
}
