package workflow_test

import (
	"errors"
	"testing"

	"github.com/stretchr/testify/mock"
	"go.temporal.io/sdk/testsuite"

	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/workflow"
)

func TestVersionOrderingWorkflow(t *testing.T) {
	t.Parallel()

	var orderingActivities *activity.VersionOrderingActivities

	tests := []struct {
		name      string
		input     workflow.VersionOrderingInput
		mockSetup func(env *testsuite.TestWorkflowEnvironment)
		wantCount int
		wantErr   bool
	}{
		{
			name: "orders versions without manifest",
			input: workflow.VersionOrderingInput{
				GroupID: "org.spongepowered", ArtifactID: "spongeapi",
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(orderingActivities.ComputeVersionOrdering, mock.Anything, mock.Anything).
					Return(&activity.ComputeVersionOrderingOutput{
						Assignments: []activity.VersionSortAssignment{
							{VersionID: 1, SortOrder: 1},
							{VersionID: 2, SortOrder: 2},
						},
					}, nil)

				env.OnActivity(orderingActivities.ApplyVersionOrdering, mock.Anything, mock.Anything).
					Return(nil)
			},
			wantCount: 2,
		},
		{
			name: "orders with Mojang manifest",
			input: workflow.VersionOrderingInput{
				GroupID: "org.spongepowered", ArtifactID: "spongevanilla",
				UseMojangManifest: true,
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(orderingActivities.FetchMojangManifest, mock.Anything, mock.Anything).
					Return(&activity.FetchMojangManifestOutput{
						VersionOrder: map[string]int{"1.21.10": 100, "1.21.9": 99},
					}, nil)

				env.OnActivity(orderingActivities.ComputeVersionOrdering, mock.Anything, mock.Anything).
					Return(&activity.ComputeVersionOrderingOutput{
						Assignments: []activity.VersionSortAssignment{
							{VersionID: 1, SortOrder: 1},
							{VersionID: 2, SortOrder: 2},
							{VersionID: 3, SortOrder: 3},
						},
					}, nil)

				env.OnActivity(orderingActivities.ApplyVersionOrdering, mock.Anything, mock.Anything).
					Return(nil)
			},
			wantCount: 3,
		},
		{
			name: "no versions returns zero",
			input: workflow.VersionOrderingInput{
				GroupID: "org.spongepowered", ArtifactID: "empty",
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(orderingActivities.ComputeVersionOrdering, mock.Anything, mock.Anything).
					Return(&activity.ComputeVersionOrderingOutput{}, nil)
			},
			wantCount: 0,
		},
		{
			name: "manifest fetch error propagates",
			input: workflow.VersionOrderingInput{
				GroupID: "org.spongepowered", ArtifactID: "spongevanilla",
				UseMojangManifest: true,
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(orderingActivities.FetchMojangManifest, mock.Anything, mock.Anything).
					Return(nil, errors.New("network error"))
			},
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			suite := &testsuite.WorkflowTestSuite{}
			env := suite.NewTestWorkflowEnvironment()
			tt.mockSetup(env)

			env.ExecuteWorkflow(workflow.VersionOrderingWorkflow, tt.input)

			if !env.IsWorkflowCompleted() {
				t.Fatal("workflow did not complete")
			}

			if tt.wantErr {
				if env.GetWorkflowError() == nil {
					t.Fatal("expected error, got nil")
				}
				return
			}

			if err := env.GetWorkflowError(); err != nil {
				t.Fatalf("unexpected workflow error: %v", err)
			}

			var result workflow.VersionOrderingOutput
			if err := env.GetWorkflowResult(&result); err != nil {
				t.Fatalf("failed to get result: %v", err)
			}

			if result.VersionsOrdered != tt.wantCount {
				t.Errorf("VersionsOrdered: want %d, got %d", tt.wantCount, result.VersionsOrdered)
			}
		})
	}
}
