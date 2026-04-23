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
			name: "fetches, stores, indexes, and orders versions",
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
						Source:     activity.VersionSourceSearch,
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

				env.OnWorkflow(workflow.VersionOrderingWorkflow, mock.Anything, mock.Anything).
					Return(&workflow.VersionOrderingOutput{VersionsOrdered: 2}, nil)

				env.OnWorkflow(workflow.CommitEnrichmentWorkflow, mock.Anything, mock.Anything).
					Return(&workflow.CommitEnrichmentOutput{}, nil)
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
		{
			name: "no new versions still triggers ordering",
			input: workflow.VersionSyncInput{
				GroupID:    "org.spongepowered",
				ArtifactID: "spongeapi",
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(activities.FetchVersions, mock.Anything, mock.Anything).
					Return(&activity.FetchVersionsOutput{
						Versions: []domain.VersionInfo{
							{GroupID: "org.spongepowered", ArtifactID: "spongeapi", Version: "8.0.0"},
						},
					}, nil)

				env.OnActivity(activities.StoreNewVersions, mock.Anything, mock.Anything).
					Return(&activity.StoreNewVersionsOutput{
						NewVersions: nil, // all versions already stored
					}, nil)

				// No batch indexing since no new versions, but ordering still runs.
				env.OnWorkflow(workflow.VersionOrderingWorkflow, mock.Anything, mock.Anything).
					Return(&workflow.VersionOrderingOutput{VersionsOrdered: 1}, nil)

				env.OnWorkflow(workflow.CommitEnrichmentWorkflow, mock.Anything, mock.Anything).
					Return(&workflow.CommitEnrichmentOutput{}, nil)
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

// TestVersionSyncWorkflowPropagatesSource verifies the workflow passes its
// input Source to the FetchVersions activity verbatim — including the
// metadata source the fast schedule will request. Regression guard against
// dropping the field during future refactors.
func TestVersionSyncWorkflowPropagatesSource(t *testing.T) {
	t.Parallel()

	var activities *activity.VersionSyncActivities

	suite := &testsuite.WorkflowTestSuite{}
	env := suite.NewTestWorkflowEnvironment()

	env.OnActivity(
		activities.FetchVersions,
		mock.Anything,
		activity.FetchVersionsInput{
			GroupID:    "org.spongepowered",
			ArtifactID: "spongeapi",
			Source:     activity.VersionSourceMetadata,
		},
	).Return(&activity.FetchVersionsOutput{}, nil)

	env.ExecuteWorkflow(workflow.VersionSyncWorkflow, workflow.VersionSyncInput{
		GroupID:    "org.spongepowered",
		ArtifactID: "spongeapi",
		Source:     activity.VersionSourceMetadata,
	})

	if !env.IsWorkflowCompleted() {
		t.Fatal("workflow did not complete")
	}
	if err := env.GetWorkflowError(); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
}
