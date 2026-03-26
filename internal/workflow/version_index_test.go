package workflow_test

import (
	"errors"
	"testing"

	"github.com/stretchr/testify/mock"
	"go.temporal.io/sdk/testsuite"

	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/workflow"
)

func TestVersionIndexWorkflow(t *testing.T) {
	t.Parallel()

	var indexActivities *activity.VersionIndexActivities

	tests := []struct {
		name      string
		input     workflow.VersionIndexInput
		mockSetup func(env *testsuite.TestWorkflowEnvironment)
		want      *workflow.VersionIndexOutput
		wantErr   bool
	}{
		{
			name: "full indexing pipeline with commit extraction",
			input: workflow.VersionIndexInput{
				GroupID: "org.spongepowered", ArtifactID: "spongeapi", Version: "8.0.0",
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(indexActivities.FetchVersionAssets, mock.Anything, mock.Anything).
					Return(&activity.FetchVersionAssetsOutput{
						Assets: []domain.AssetInfo{
							{DownloadURL: "https://repo.example/a.jar", Extension: "jar", Classifier: "universal"},
						},
					}, nil)

				env.OnActivity(indexActivities.StoreVersionAssets, mock.Anything, mock.Anything).
					Return(&activity.StoreVersionAssetsOutput{StoredCount: 1}, nil)

				// InspectJarsForCommits is now inlined (pure function, not an activity)

				env.OnActivity(indexActivities.ExtractCommitFromJar, mock.Anything, mock.Anything).
					Return(&activity.ExtractCommitFromJarOutput{
						CommitInfo: &domain.CommitInfo{Sha: "abc123"},
					}, nil)

				env.OnActivity(indexActivities.StoreCommitInfo, mock.Anything, mock.Anything).
					Return(nil)
			},
			want: &workflow.VersionIndexOutput{
				AssetsStored: 1,
				CommitFound:  true,
			},
		},
		{
			name: "no assets returns empty output",
			input: workflow.VersionIndexInput{
				GroupID: "org.spongepowered", ArtifactID: "spongeapi", Version: "8.0.0",
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(indexActivities.FetchVersionAssets, mock.Anything, mock.Anything).
					Return(&activity.FetchVersionAssetsOutput{Assets: nil}, nil)
			},
			want: &workflow.VersionIndexOutput{},
		},
		{
			name: "fetch error propagates",
			input: workflow.VersionIndexInput{
				GroupID: "org.spongepowered", ArtifactID: "spongeapi", Version: "8.0.0",
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(indexActivities.FetchVersionAssets, mock.Anything, mock.Anything).
					Return(nil, errors.New("search API unavailable"))
			},
			wantErr: true,
		},
		{
			name: "no jar candidates skips commit extraction",
			input: workflow.VersionIndexInput{
				GroupID: "org.spongepowered", ArtifactID: "spongeapi", Version: "8.0.0",
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(indexActivities.FetchVersionAssets, mock.Anything, mock.Anything).
					Return(&activity.FetchVersionAssetsOutput{
						Assets: []domain.AssetInfo{
							{DownloadURL: "https://repo.example/a.pom", Extension: "pom"},
						},
					}, nil)

				env.OnActivity(indexActivities.StoreVersionAssets, mock.Anything, mock.Anything).
					Return(&activity.StoreVersionAssetsOutput{StoredCount: 1}, nil)

				// No jar candidates — PickBestJarCandidate returns nil for .pom only
			},
			want: &workflow.VersionIndexOutput{
				AssetsStored: 1,
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

			env.ExecuteWorkflow(workflow.VersionIndexWorkflow, tt.input)

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

			var result workflow.VersionIndexOutput
			if err := env.GetWorkflowResult(&result); err != nil {
				t.Fatalf("failed to get workflow result: %v", err)
			}

			if result.AssetsStored != tt.want.AssetsStored {
				t.Errorf("AssetsStored: want %d, got %d", tt.want.AssetsStored, result.AssetsStored)
			}
			if result.CommitFound != tt.want.CommitFound {
				t.Errorf("CommitFound: want %v, got %v", tt.want.CommitFound, result.CommitFound)
			}
		})
	}
}
