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
			name: "orders versions without schema",
			input: workflow.VersionOrderingInput{
				GroupID: "org.spongepowered", ArtifactID: "spongeapi",
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(orderingActivities.FetchVersionSchema, mock.Anything, mock.Anything).
					Return(&activity.FetchVersionSchemaOutput{}, nil)

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
			name: "orders with Mojang manifest from schema",
			input: workflow.VersionOrderingInput{
				GroupID: "org.spongepowered", ArtifactID: "spongevanilla",
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(orderingActivities.FetchVersionSchema, mock.Anything, mock.Anything).
					Return(&activity.FetchVersionSchemaOutput{
						Schema: &domain.VersionSchema{UseMojangManifest: true},
					}, nil)

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
			name: "stores schema-extracted tags",
			input: workflow.VersionOrderingInput{
				GroupID: "org.spongepowered", ArtifactID: "spongevanilla",
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(orderingActivities.FetchVersionSchema, mock.Anything, mock.Anything).
					Return(&activity.FetchVersionSchemaOutput{
						Schema: &domain.VersionSchema{UseMojangManifest: false},
					}, nil)

				env.OnActivity(orderingActivities.ComputeVersionOrdering, mock.Anything, mock.Anything).
					Return(&activity.ComputeVersionOrderingOutput{
						Assignments: []activity.VersionSortAssignment{
							{VersionID: 1, SortOrder: 1},
						},
						VersionTags: []activity.VersionTagSet{
							{VersionID: 1, Tags: map[string]string{"minecraft": "1.21.10", "api": "17.0.0"}},
						},
					}, nil)

				env.OnActivity(orderingActivities.ApplyVersionOrdering, mock.Anything, mock.Anything).
					Return(nil)

				env.OnActivity(orderingActivities.StoreVersionTags, mock.Anything, mock.Anything).
					Return(nil)
			},
			wantCount: 1,
		},
		{
			name: "no versions returns zero",
			input: workflow.VersionOrderingInput{
				GroupID: "org.spongepowered", ArtifactID: "empty",
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(orderingActivities.FetchVersionSchema, mock.Anything, mock.Anything).
					Return(&activity.FetchVersionSchemaOutput{}, nil)

				env.OnActivity(orderingActivities.ComputeVersionOrdering, mock.Anything, mock.Anything).
					Return(&activity.ComputeVersionOrderingOutput{}, nil)
			},
			wantCount: 0,
		},
		{
			name: "schema fetch error propagates",
			input: workflow.VersionOrderingInput{
				GroupID: "org.spongepowered", ArtifactID: "spongevanilla",
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(orderingActivities.FetchVersionSchema, mock.Anything, mock.Anything).
					Return(nil, errors.New("db error"))
			},
			wantErr: true,
		},
		{
			name: "manifest fetch error propagates",
			input: workflow.VersionOrderingInput{
				GroupID: "org.spongepowered", ArtifactID: "spongevanilla",
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(orderingActivities.FetchVersionSchema, mock.Anything, mock.Anything).
					Return(&activity.FetchVersionSchemaOutput{
						Schema: &domain.VersionSchema{UseMojangManifest: true},
					}, nil)

				env.OnActivity(orderingActivities.FetchMojangManifest, mock.Anything, mock.Anything).
					Return(nil, errors.New("network error"))
			},
			wantErr: true,
		},
		{
			name: "batches large assignment and tag sets",
			input: workflow.VersionOrderingInput{
				GroupID: "org.spongepowered", ArtifactID: "spongevanilla",
			},
			mockSetup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(orderingActivities.FetchVersionSchema, mock.Anything, mock.Anything).
					Return(&activity.FetchVersionSchemaOutput{
						Schema: &domain.VersionSchema{UseMojangManifest: false},
					}, nil)

				// Build 1200 assignments and tags to force multiple batches
				// (batch size is 500, so we expect 3 calls for assignments
				// and 3 calls for tags).
				assignments := make([]activity.VersionSortAssignment, 1200)
				tags := make([]activity.VersionTagSet, 1200)
				for i := 0; i < 1200; i++ {
					assignments[i] = activity.VersionSortAssignment{
						VersionID: int64(i + 1), SortOrder: int32(i + 1),
					}
					tags[i] = activity.VersionTagSet{
						VersionID: int64(i + 1),
						Tags:      map[string]string{"minecraft": "1.21.10"},
					}
				}

				env.OnActivity(orderingActivities.ComputeVersionOrdering, mock.Anything, mock.Anything).
					Return(&activity.ComputeVersionOrderingOutput{
						Assignments: assignments,
						VersionTags: tags,
					}, nil)

				env.OnActivity(orderingActivities.ApplyVersionOrdering, mock.Anything, mock.Anything).
					Return(nil)

				env.OnActivity(orderingActivities.StoreVersionTags, mock.Anything, mock.Anything).
					Return(nil)
			},
			wantCount: 1200,
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
