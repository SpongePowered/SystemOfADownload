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

func TestVersionSyncWorkflow(t *testing.T) {
	t.Parallel()

	var activities *activity.VersionSyncActivities

	tests := []struct {
		name      string
		input     workflow.VersionSyncInput
		mockSetup func(env *testsuite.TestWorkflowEnvironment)
		wantCount int
		wantErr   bool
	}{
		{
			name: "fetches and stores new versions",
			input: workflow.VersionSyncInput{
				GroupID:    "org.spongepowered",
				ArtifactID: "spongeapi",
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(
					activities.FetchVersions,
					mock.Anything,
					activity.FetchVersionsInput{
						GroupID:    "org.spongepowered",
						ArtifactID: "spongeapi",
					},
				).Return(&activity.FetchVersionsOutput{
					Versions: []domain.VersionInfo{
						{GroupID: "org.spongepowered", ArtifactID: "spongeapi", Version: "8.0.0"},
						{GroupID: "org.spongepowered", ArtifactID: "spongeapi", Version: "7.4.0"},
					},
				}, nil)

				env.OnActivity(
					activities.StoreNewVersions,
					mock.Anything,
					activity.StoreNewVersionsInput{
						GroupID:    "org.spongepowered",
						ArtifactID: "spongeapi",
						Versions: []domain.VersionInfo{
							{GroupID: "org.spongepowered", ArtifactID: "spongeapi", Version: "8.0.0"},
							{GroupID: "org.spongepowered", ArtifactID: "spongeapi", Version: "7.4.0"},
						},
					},
				).Return(&activity.StoreNewVersionsOutput{
					NewVersions: []domain.VersionInfo{
						{GroupID: "org.spongepowered", ArtifactID: "spongeapi", Version: "8.0.0"},
					},
				}, nil)

				env.OnWorkflow(workflow.VersionBatchIndexWorkflow, mock.Anything, mock.Anything).
					Return(1, nil)
			},
			wantCount: 1,
		},
		{
			name: "fetch error propagates",
			input: workflow.VersionSyncInput{
				GroupID:    "org.spongepowered",
				ArtifactID: "spongeapi",
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(
					activities.FetchVersions,
					mock.Anything,
					mock.Anything,
				).Return(nil, errors.New("connection refused"))
			},
			wantErr: true,
		},
		{
			name: "store error propagates",
			input: workflow.VersionSyncInput{
				GroupID:    "org.spongepowered",
				ArtifactID: "spongeapi",
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(
					activities.FetchVersions,
					mock.Anything,
					mock.Anything,
				).Return(&activity.FetchVersionsOutput{
					Versions: []domain.VersionInfo{
						{GroupID: "org.spongepowered", ArtifactID: "spongeapi", Version: "8.0.0"},
					},
				}, nil)

				env.OnActivity(
					activities.StoreNewVersions,
					mock.Anything,
					mock.Anything,
				).Return(nil, errors.New("db error"))
			},
			wantErr: true,
		},
		{
			name: "no versions found skips store",
			input: workflow.VersionSyncInput{
				GroupID:    "org.nonexistent",
				ArtifactID: "nothing",
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(
					activities.FetchVersions,
					mock.Anything,
					mock.Anything,
				).Return(&activity.FetchVersionsOutput{
					Versions: []domain.VersionInfo{},
				}, nil)
			},
			wantCount: 0,
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

			env.ExecuteWorkflow(workflow.VersionSyncWorkflow, tt.input)

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

			var result workflow.VersionSyncOutput
			if err := env.GetWorkflowResult(&result); err != nil {
				t.Fatalf("failed to get workflow result: %v", err)
			}

			if result.NewVersionsStored != tt.wantCount {
				t.Errorf("expected %d new versions stored, got %d", tt.wantCount, result.NewVersionsStored)
			}
		})
	}
}
