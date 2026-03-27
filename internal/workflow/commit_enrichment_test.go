package workflow_test

import (
	"testing"

	"github.com/stretchr/testify/mock"
	"go.temporal.io/sdk/testsuite"

	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/workflow"
)

func TestCommitEnrichmentWorkflow(t *testing.T) {
	t.Parallel()

	var changelogActs *activity.ChangelogActivities

	tests := []struct {
		name      string
		input     workflow.CommitEnrichmentInput
		mockSetup func(env *testsuite.TestWorkflowEnvironment)
		want      *workflow.CommitEnrichmentOutput
		wantErr   bool
	}{
		{
			name:  "runs enrichment and changelog phases",
			input: workflow.CommitEnrichmentInput{GroupID: "org.spongepowered", ArtifactID: "spongevanilla"},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(changelogActs.FetchVersionsForEnrichment, mock.Anything, mock.Anything).
					Return(&activity.FetchVersionsForEnrichmentOutput{
						GitRepositories: []string{"https://github.com/SpongePowered/SpongeVanilla"},
						Versions: []activity.VersionForEnrichment{
							{ID: 10, ArtifactID: 1, Version: "1.12.2-7.4.7", SortOrder: 100, CommitSha: "abc123"},
						},
					}, nil)

				env.OnWorkflow(workflow.EnrichmentBatchWorkflow, mock.Anything, mock.Anything).
					Return(1, nil)

				env.OnWorkflow(workflow.ChangelogBatchWorkflow, mock.Anything, mock.Anything).
					Return(1, nil)
			},
			want: &workflow.CommitEnrichmentOutput{
				VersionsEnriched:   1,
				ChangelogsComputed: 1,
			},
		},
		{
			name:  "no versions needing enrichment returns empty",
			input: workflow.CommitEnrichmentInput{GroupID: "org.spongepowered", ArtifactID: "spongevanilla"},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(changelogActs.FetchVersionsForEnrichment, mock.Anything, mock.Anything).
					Return(&activity.FetchVersionsForEnrichmentOutput{
						GitRepositories: []string{},
						Versions:        []activity.VersionForEnrichment{},
					}, nil)
			},
			want: &workflow.CommitEnrichmentOutput{},
		},
		{
			name:  "fetch error propagates",
			input: workflow.CommitEnrichmentInput{GroupID: "org.spongepowered", ArtifactID: "nonexistent"},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(changelogActs.FetchVersionsForEnrichment, mock.Anything, mock.Anything).
					Return(nil, assert_error("artifact not found"))
			},
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			suite := &testsuite.WorkflowTestSuite{}
			env := suite.NewTestWorkflowEnvironment()

			if tt.mockSetup != nil {
				tt.mockSetup(env)
			}

			env.ExecuteWorkflow(workflow.CommitEnrichmentWorkflow, tt.input)

			if !env.IsWorkflowCompleted() {
				t.Fatal("workflow did not complete")
			}

			if tt.wantErr {
				if env.GetWorkflowError() == nil {
					t.Fatal("expected workflow error, got nil")
				}
				return
			}

			if err := env.GetWorkflowError(); err != nil {
				t.Fatalf("unexpected workflow error: %v", err)
			}

			var result workflow.CommitEnrichmentOutput
			if err := env.GetWorkflowResult(&result); err != nil {
				t.Fatalf("failed to get workflow result: %v", err)
			}

			if result.VersionsEnriched != tt.want.VersionsEnriched {
				t.Errorf("VersionsEnriched: want %d, got %d", tt.want.VersionsEnriched, result.VersionsEnriched)
			}
			if result.ChangelogsComputed != tt.want.ChangelogsComputed {
				t.Errorf("ChangelogsComputed: want %d, got %d", tt.want.ChangelogsComputed, result.ChangelogsComputed)
			}
		})
	}
}

func TestEnrichVersionWorkflow(t *testing.T) {
	t.Parallel()

	var gitActs *activity.GitActivities
	var changelogActs *activity.ChangelogActivities

	tests := []struct {
		name      string
		input     workflow.EnrichVersionInput
		mockSetup func(env *testsuite.TestWorkflowEnvironment)
		wantErr   bool
	}{
		{
			name: "enriches version with commit details and submodules",
			input: workflow.EnrichVersionInput{
				VersionID: 10, ArtifactID: 1, Version: "1.12.2-7.4.7",
				CommitSha:       "abc123",
				GitRepositories: []string{"https://github.com/SpongePowered/SpongeVanilla"},
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(gitActs.EnsureRepoCloned, mock.Anything, activity.EnsureRepoClonedInput{
					RepoURL: "https://github.com/SpongePowered/SpongeVanilla",
				}).Return(&activity.EnsureRepoClonedOutput{LocalPath: "/cache/repo.git"}, nil)

				env.OnActivity(gitActs.GetCommitDetails, mock.Anything, activity.GetCommitDetailsInput{
					RepoPath: "/cache/repo.git", Sha: "abc123",
				}).Return(&activity.GetCommitDetailsOutput{
					Sha: "abc123", Message: "Release 7.4.7",
					AuthorName: "Test", AuthorEmail: "test@example.com",
					CommitDate: "2026-03-22T12:00:00Z",
				}, nil)

				env.OnActivity(gitActs.ResolveSubmodules, mock.Anything, activity.ResolveSubmodulesInput{
					RepoPath: "/cache/repo.git", Sha: "abc123",
				}).Return(&activity.ResolveSubmodulesOutput{
					Submodules: []activity.SubmoduleRefOutput{
						{Path: "SpongeCommon", URL: "https://github.com/SpongePowered/SpongeCommon.git", Sha: "sub123"},
					},
				}, nil)

				// Submodule clone + details
				env.OnActivity(gitActs.EnsureRepoCloned, mock.Anything, activity.EnsureRepoClonedInput{
					RepoURL: "https://github.com/SpongePowered/SpongeCommon.git",
				}).Return(&activity.EnsureRepoClonedOutput{LocalPath: "/cache/common.git"}, nil)

				env.OnActivity(gitActs.GetCommitDetails, mock.Anything, activity.GetCommitDetailsInput{
					RepoPath: "/cache/common.git", Sha: "sub123",
				}).Return(&activity.GetCommitDetailsOutput{
					Sha: "sub123", Message: "Common release",
					AuthorName: "Dev", AuthorEmail: "dev@example.com",
					CommitDate: "2026-03-22T11:00:00Z",
				}, nil)

				env.OnActivity(changelogActs.StoreEnrichedCommit, mock.Anything, mock.Anything).
					Return(nil)
			},
		},
		{
			name: "commit not found in any repo stores error marker",
			input: workflow.EnrichVersionInput{
				VersionID: 10, ArtifactID: 1, Version: "1.12.2-7.4.7",
				CommitSha:       "missing",
				GitRepositories: []string{"https://github.com/SpongePowered/SpongeVanilla"},
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(gitActs.EnsureRepoCloned, mock.Anything, mock.Anything).
					Return(&activity.EnsureRepoClonedOutput{LocalPath: "/cache/repo.git"}, nil)

				env.OnActivity(gitActs.GetCommitDetails, mock.Anything, mock.Anything).
					Return(nil, assert_error("commit not found"))

				// Should store error marker
				env.OnActivity(changelogActs.StoreEnrichedCommit, mock.Anything, mock.MatchedBy(func(input activity.StoreEnrichedCommitInput) bool {
					return input.CommitInfo.ChangelogStatus == "error_commit_not_found"
				})).Return(nil)
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			suite := &testsuite.WorkflowTestSuite{}
			env := suite.NewTestWorkflowEnvironment()

			if tt.mockSetup != nil {
				tt.mockSetup(env)
			}

			env.ExecuteWorkflow(workflow.EnrichVersionWorkflow, tt.input)

			if !env.IsWorkflowCompleted() {
				t.Fatal("workflow did not complete")
			}

			if tt.wantErr {
				if env.GetWorkflowError() == nil {
					t.Fatal("expected workflow error, got nil")
				}
				return
			}

			if err := env.GetWorkflowError(); err != nil {
				t.Fatalf("unexpected workflow error: %v", err)
			}
		})
	}
}

func TestChangelogVersionWorkflow(t *testing.T) {
	t.Parallel()

	var changelogActs *activity.ChangelogActivities
	var gitActs *activity.GitActivities

	tests := []struct {
		name      string
		input     workflow.ChangelogVersionInput
		mockSetup func(env *testsuite.TestWorkflowEnvironment)
		wantErr   bool
	}{
		{
			name: "computes changelog between consecutive versions",
			input: workflow.ChangelogVersionInput{
				VersionID: 11, ArtifactID: 1, Version: "1.12.2-7.4.7",
				SortOrder: 100, CommitSha: "abc123",
				Repository:      "https://github.com/SpongePowered/Repo",
				GitRepositories: []string{"https://github.com/SpongePowered/Repo"},
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(changelogActs.GetPreviousVersionCommit, mock.Anything, mock.Anything).
					Return(&activity.GetPreviousVersionCommitOutput{
						Found: true, ID: 10, Version: "1.12.2-7.4.6",
						CommitInfo: &domain.CommitInfo{
							Sha:        "prev456",
							Repository: "https://github.com/SpongePowered/Repo",
							EnrichedAt: "2026-03-22T12:00:00Z",
						},
					}, nil)

				env.OnActivity(gitActs.ResolveRepo, mock.Anything, mock.Anything).
					Return(&activity.EnsureRepoClonedOutput{LocalPath: "/cache/repo.git"}, nil)

				env.OnActivity(gitActs.ComputeChangelog, mock.Anything, activity.ComputeChangelogInput{
					RepoPath: "/cache/repo.git", FromSHA: "prev456", ToSHA: "abc123",
				}).Return(&activity.ComputeChangelogOutput{
					Commits: []activity.ChangelogCommit{
						{Sha: "abc123", Message: "Release 7.4.7", AuthorName: "Test", AuthorEmail: "t@e.com", CommitDate: "2026-03-22T12:00:00Z"},
					},
				}, nil)

				env.OnActivity(gitActs.ResolveSubmodules, mock.Anything, mock.Anything).
					Return(&activity.ResolveSubmodulesOutput{}, nil)

				env.OnActivity(changelogActs.StoreChangelog, mock.Anything, mock.Anything).
					Return(nil)
			},
		},
		{
			name: "no predecessor skips changelog",
			input: workflow.ChangelogVersionInput{
				VersionID: 10, ArtifactID: 1, Version: "1.12.2-7.1.0",
				SortOrder: 1, CommitSha: "first123",
				GitRepositories: []string{"https://github.com/SpongePowered/Repo"},
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(changelogActs.GetPreviousVersionCommit, mock.Anything, mock.Anything).
					Return(&activity.GetPreviousVersionCommitOutput{Found: false}, nil)
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			suite := &testsuite.WorkflowTestSuite{}
			env := suite.NewTestWorkflowEnvironment()

			if tt.mockSetup != nil {
				tt.mockSetup(env)
			}

			env.ExecuteWorkflow(workflow.ChangelogVersionWorkflow, tt.input)

			if !env.IsWorkflowCompleted() {
				t.Fatal("workflow did not complete")
			}

			if tt.wantErr {
				if env.GetWorkflowError() == nil {
					t.Fatal("expected workflow error, got nil")
				}
				return
			}

			if err := env.GetWorkflowError(); err != nil {
				t.Fatalf("unexpected workflow error: %v", err)
			}
		})
	}
}

// assert_error is a helper that returns an error for use in mock returns.
func assert_error(msg string) error {
	return &testError{msg}
}

type testError struct{ msg string }

func (e *testError) Error() string { return e.msg }
