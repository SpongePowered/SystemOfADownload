package activity_test

import (
	"encoding/json"
	"testing"

	"github.com/google/go-cmp/cmp"
	"github.com/jackc/pgx/v5"

	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
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
