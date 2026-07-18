package activity_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/google/go-cmp/cmp"
	"github.com/jackc/pgx/v5"
	"github.com/stretchr/testify/mock"

	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/githubapi"
	"github.com/spongepowered/systemofadownload/internal/repository"
	repomocks "github.com/spongepowered/systemofadownload/internal/repository/mocks"
)

func TestFetchVersionsForEnrichment(t *testing.T) {
	t.Parallel()

	commitJSON := func(sha, repo string) []byte {
		data, _ := json.Marshal(domain.CommitInfo{Sha: sha, Repository: repo})
		return data
	}

	tests := []struct {
		name      string
		input     activity.FetchVersionsForEnrichmentInput
		mockSetup func(t *testing.T, m *repomocks.MockRepository)
		want      *activity.FetchVersionsForEnrichmentOutput
		wantErr   bool
	}{
		{
			name:  "returns versions needing enrichment with git repos",
			input: activity.FetchVersionsForEnrichmentInput{GroupID: "org.spongepowered", ArtifactID: "spongevanilla"},
			mockSetup: func(t *testing.T, m *repomocks.MockRepository) {
				m.EXPECT().GetArtifactByGroupAndId(t.Context(), db.GetArtifactByGroupAndIdParams{
					GroupID: "org.spongepowered", ArtifactID: "spongevanilla",
				}).Return(db.Artifact{
					ID:              1,
					GitRepositories: []byte(`["https://github.com/SpongePowered/SpongeVanilla"]`),
				}, nil)

				m.EXPECT().ListVersionsNeedingEnrichment(t.Context(), db.ListVersionsNeedingEnrichmentParams{
					GroupID: "org.spongepowered", ArtifactID: "spongevanilla",
				}).Return([]db.ArtifactVersion{
					{ID: 10, ArtifactID: 1, Version: "1.12.2-7.4.7", SortOrder: 100, CommitBody: commitJSON("abc123", "")},
					{ID: 11, ArtifactID: 1, Version: "1.12.2-7.4.6", SortOrder: 99, CommitBody: commitJSON("def456", "")},
				}, nil)
			},
			want: &activity.FetchVersionsForEnrichmentOutput{
				GitRepositories: []string{"https://github.com/SpongePowered/SpongeVanilla"},
				Versions: []activity.VersionForEnrichment{
					{ID: 10, ArtifactID: 1, Version: "1.12.2-7.4.7", SortOrder: 100, CommitSha: "abc123"},
					{ID: 11, ArtifactID: 1, Version: "1.12.2-7.4.6", SortOrder: 99, CommitSha: "def456"},
				},
			},
		},
		{
			name:  "skips versions with empty SHA",
			input: activity.FetchVersionsForEnrichmentInput{GroupID: "org.spongepowered", ArtifactID: "spongevanilla"},
			mockSetup: func(t *testing.T, m *repomocks.MockRepository) {
				m.EXPECT().GetArtifactByGroupAndId(t.Context(), db.GetArtifactByGroupAndIdParams{
					GroupID: "org.spongepowered", ArtifactID: "spongevanilla",
				}).Return(db.Artifact{ID: 1, GitRepositories: []byte(`[]`)}, nil)

				m.EXPECT().ListVersionsNeedingEnrichment(t.Context(), db.ListVersionsNeedingEnrichmentParams{
					GroupID: "org.spongepowered", ArtifactID: "spongevanilla",
				}).Return([]db.ArtifactVersion{
					{ID: 10, ArtifactID: 1, Version: "1.12.2-7.4.7", SortOrder: 100, CommitBody: commitJSON("", "")},
				}, nil)
			},
			want: &activity.FetchVersionsForEnrichmentOutput{
				GitRepositories: []string{},
				Versions:        []activity.VersionForEnrichment{},
			},
		},
		{
			name:  "artifact not found propagates error",
			input: activity.FetchVersionsForEnrichmentInput{GroupID: "org.spongepowered", ArtifactID: "nonexistent"},
			mockSetup: func(t *testing.T, m *repomocks.MockRepository) {
				m.EXPECT().GetArtifactByGroupAndId(t.Context(), db.GetArtifactByGroupAndIdParams{
					GroupID: "org.spongepowered", ArtifactID: "nonexistent",
				}).Return(db.Artifact{}, pgx.ErrNoRows)
			},
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			mockRepo := repomocks.NewMockRepository(t)
			if tt.mockSetup != nil {
				tt.mockSetup(t, mockRepo)
			}

			acts := &activity.ChangelogActivities{Repo: mockRepo}
			got, err := acts.FetchVersionsForEnrichment(t.Context(), tt.input)

			if tt.wantErr {
				if err == nil {
					t.Fatal("expected error, got nil")
				}
				return
			}
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if diff := cmp.Diff(tt.want, got); diff != "" {
				t.Errorf("mismatch (-want +got):\n%s", diff)
			}
		})
	}
}

func TestGetPreviousVersionCommit(t *testing.T) {
	t.Parallel()

	enrichedJSON, _ := json.Marshal(domain.CommitInfo{
		Sha: "abc123", Repository: "https://github.com/SpongePowered/SpongeVanilla",
		EnrichedAt: "2026-03-22T12:00:00Z",
	})

	tests := []struct {
		name      string
		input     activity.GetPreviousVersionCommitInput
		mockSetup func(t *testing.T, m *repomocks.MockRepository)
		wantFound bool
		wantSha   string
	}{
		{
			name:  "returns previous version",
			input: activity.GetPreviousVersionCommitInput{ArtifactID: 1, SortOrder: 100},
			mockSetup: func(t *testing.T, m *repomocks.MockRepository) {
				m.EXPECT().GetPreviousVersion(t.Context(), db.GetPreviousVersionParams{
					ArtifactID: 1, SortOrder: 100,
				}).Return(db.ArtifactVersion{
					ID: 9, Version: "1.12.2-7.4.6", CommitBody: enrichedJSON,
				}, nil)
			},
			wantFound: true,
			wantSha:   "abc123",
		},
		{
			name:  "no previous version returns not found",
			input: activity.GetPreviousVersionCommitInput{ArtifactID: 1, SortOrder: 1},
			mockSetup: func(t *testing.T, m *repomocks.MockRepository) {
				m.EXPECT().GetPreviousVersion(t.Context(), db.GetPreviousVersionParams{
					ArtifactID: 1, SortOrder: 1,
				}).Return(db.ArtifactVersion{}, pgx.ErrNoRows)
			},
			wantFound: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			mockRepo := repomocks.NewMockRepository(t)
			if tt.mockSetup != nil {
				tt.mockSetup(t, mockRepo)
			}

			acts := &activity.ChangelogActivities{Repo: mockRepo}
			got, err := acts.GetPreviousVersionCommit(t.Context(), tt.input)
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}

			if got.Found != tt.wantFound {
				t.Errorf("Found: want %v, got %v", tt.wantFound, got.Found)
			}
			if tt.wantFound && got.CommitInfo.Sha != tt.wantSha {
				t.Errorf("SHA: want %s, got %s", tt.wantSha, got.CommitInfo.Sha)
			}
		})
	}
}

func TestCheckPreviousVersionEnriched(t *testing.T) {
	t.Parallel()

	mockRepo := repomocks.NewMockRepository(t)
	mockRepo.EXPECT().IsVersionEnriched(t.Context(), int64(10)).Return(true, nil)
	mockRepo.EXPECT().IsVersionEnriched(t.Context(), int64(11)).Return(false, nil)

	acts := &activity.ChangelogActivities{Repo: mockRepo}

	enriched, err := acts.CheckPreviousVersionEnriched(t.Context(), activity.CheckPreviousVersionEnrichedInput{VersionID: 10})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !enriched {
		t.Error("expected enriched=true for version 10")
	}

	enriched, err = acts.CheckPreviousVersionEnriched(t.Context(), activity.CheckPreviousVersionEnrichedInput{VersionID: 11})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if enriched {
		t.Error("expected enriched=false for version 11")
	}
}

func TestStoreCommitDataResolvesGitHubUsernames(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name       string
		run        func(context.Context, *activity.ChangelogActivities) error
		mockSetup  func(*repomocks.MockRepository, *repomocks.MockTx)
		assertBody func(*testing.T, domain.CommitInfo)
	}{
		{
			name: "enriched commit and submodule",
			run: func(ctx context.Context, acts *activity.ChangelogActivities) error {
				return acts.StoreEnrichedCommit(ctx, activity.StoreEnrichedCommitInput{
					VersionID: 10,
					CommitInfo: domain.CommitInfo{
						Sha:        "main-sha",
						Repository: "https://github.com/SpongePowered/Sponge",
						Author: &domain.CommitAuthor{
							Name:  "Main Author",
							Email: "123+main-user@users.noreply.github.com",
						},
						Submodules: []domain.SubmoduleCommit{{
							Repository: "https://github.com/SpongePowered/SpongeAPI",
							Sha:        "sub-sha",
							Author: &domain.CommitAuthor{
								Name:  "Sub Author",
								Email: "sub-user@users.noreply.github.com",
							},
						}},
					},
				})
			},
			mockSetup: func(repo *repomocks.MockRepository, tx *repomocks.MockTx) {
				expectCommitBodyUpdate(repo, tx)
			},
			assertBody: func(t *testing.T, info domain.CommitInfo) {
				t.Helper()
				if got := info.Author.GitHubUsername; got != "main-user" {
					t.Errorf("main GitHub username = %q, want main-user", got)
				}
				if got := info.Submodules[0].Author.GitHubUsername; got != "sub-user" {
					t.Errorf("submodule GitHub username = %q, want sub-user", got)
				}
			},
		},
		{
			name: "main and nested changelogs",
			run: func(ctx context.Context, acts *activity.ChangelogActivities) error {
				return acts.StoreChangelog(ctx, activity.StoreChangelogInput{
					VersionID: 11,
					Changelog: domain.Changelog{
						Commits: []domain.CommitSummary{{
							Sha: "main-sha",
							URL: "https://github.com/SpongePowered/Sponge/commit/main-sha",
							Author: &domain.CommitAuthor{
								Name:  "Main Author",
								Email: "main-user@users.noreply.github.com",
							},
						}},
						SubmoduleChangelogs: map[string]*domain.Changelog{
							"https://github.com/SpongePowered/SpongeAPI": {
								Commits: []domain.CommitSummary{{
									Sha: "sub-sha",
									Author: &domain.CommitAuthor{
										Name:  "Sub Author",
										Email: "123+sub-user@users.noreply.github.com",
									},
								}},
							},
						},
					},
				})
			},
			mockSetup: func(repo *repomocks.MockRepository, tx *repomocks.MockTx) {
				tx.EXPECT().GetArtifactVersionByID(mock.Anything, int64(11)).
					Return(db.ArtifactVersion{ID: 11, CommitBody: []byte(`{"sha":"head"}`)}, nil)
				expectCommitBodyUpdate(repo, tx)
			},
			assertBody: func(t *testing.T, info domain.CommitInfo) {
				t.Helper()
				if got := info.Changelog.Commits[0].Author.GitHubUsername; got != "main-user" {
					t.Errorf("changelog GitHub username = %q, want main-user", got)
				}
				nested := info.Changelog.SubmoduleChangelogs["https://github.com/SpongePowered/SpongeAPI"]
				if got := nested.Commits[0].Author.GitHubUsername; got != "sub-user" {
					t.Errorf("nested GitHub username = %q, want sub-user", got)
				}
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			repo := repomocks.NewMockRepository(t)
			tx := repomocks.NewMockTx(t)
			var stored domain.CommitInfo

			tt.mockSetup(repo, tx)
			tx.EXPECT().UpdateArtifactVersionCommitBody(mock.Anything, mock.MatchedBy(func(params db.UpdateArtifactVersionCommitBodyParams) bool {
				return json.Unmarshal(params.CommitBody, &stored) == nil
			})).Return(nil)

			acts := &activity.ChangelogActivities{
				Repo:   repo,
				GitHub: githubapi.NewClient(nil, ""),
			}
			if err := tt.run(t.Context(), acts); err != nil {
				t.Fatalf("store commit data: %v", err)
			}
			tt.assertBody(t, stored)
		})
	}
}

func expectCommitBodyUpdate(repo *repomocks.MockRepository, tx *repomocks.MockTx) {
	repo.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
		func(ctx context.Context, fn func(repository.Tx) error) error {
			return fn(tx)
		},
	)
}

func TestStoreEnrichedCommitReservesPersistenceTime(t *testing.T) {
	t.Parallel()

	server := httptest.NewServer(http.HandlerFunc(func(_ http.ResponseWriter, r *http.Request) {
		<-r.Context().Done()
	}))
	defer server.Close()

	repo := repomocks.NewMockRepository(t)
	tx := repomocks.NewMockTx(t)
	var stored domain.CommitInfo

	repo.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
		func(ctx context.Context, fn func(repository.Tx) error) error {
			if err := ctx.Err(); err != nil {
				t.Errorf("persistence context already canceled: %v", err)
			}
			return fn(tx)
		},
	)
	tx.EXPECT().UpdateArtifactVersionCommitBody(mock.Anything, mock.MatchedBy(func(params db.UpdateArtifactVersionCommitBodyParams) bool {
		return json.Unmarshal(params.CommitBody, &stored) == nil
	})).Return(nil)

	acts := &activity.ChangelogActivities{
		Repo:   repo,
		GitHub: githubapi.NewClientWithBaseURL(server.Client(), "", server.URL),
	}
	ctx, cancel := context.WithTimeout(t.Context(), 5500*time.Millisecond)
	defer cancel()

	err := acts.StoreEnrichedCommit(ctx, activity.StoreEnrichedCommitInput{
		VersionID: 10,
		CommitInfo: domain.CommitInfo{
			Sha:        "abc123",
			Repository: "https://github.com/SpongePowered/Sponge",
			Author: &domain.CommitAuthor{
				Name:  "Git Author",
				Email: "author@example.com",
			},
		},
	})
	if err != nil {
		t.Fatalf("StoreEnrichedCommit() error = %v", err)
	}
	if stored.Author.GitHubUsername != "" {
		t.Errorf("GitHubUsername = %q, want fallback without username", stored.Author.GitHubUsername)
	}
}
