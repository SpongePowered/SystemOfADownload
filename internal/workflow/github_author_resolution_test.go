package workflow_test

import (
	"testing"

	"github.com/stretchr/testify/mock"
	"go.temporal.io/sdk/testsuite"
	sdkworkflow "go.temporal.io/sdk/workflow"

	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/workflow"
)

func TestGitHubAuthorResolutionWorkflow(t *testing.T) {
	t.Parallel()

	var activities *activity.GitHubAuthorResolutionActivities
	tests := []struct {
		name         string
		page         activity.FetchGitHubAuthorResolutionPageOutput
		setup        func(*testsuite.TestWorkflowEnvironment)
		wantContinue bool
	}{
		{
			name: "completes on empty page",
			page: activity.FetchGitHubAuthorResolutionPageOutput{},
		},
		{
			name: "resolves page and continues as new",
			page: activity.FetchGitHubAuthorResolutionPageOutput{
				VersionIDs: []int64{10, 9},
				NextBeforeID: func() *int64 {
					value := int64(9)
					return &value
				}(),
			},
			setup: func(env *testsuite.TestWorkflowEnvironment) {
				env.OnActivity(
					activities.ResolveGitHubAuthorsBatch,
					mock.Anything,
					activity.ResolveGitHubAuthorsBatchInput{VersionIDs: []int64{10, 9}},
				).Return(nil)
			},
			wantContinue: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			var suite testsuite.WorkflowTestSuite
			env := suite.NewTestWorkflowEnvironment()
			env.OnActivity(
				activities.FetchGitHubAuthorResolutionPage,
				mock.Anything,
				activity.FetchGitHubAuthorResolutionPageInput{PageSize: 50},
			).Return(&tt.page, nil)
			if tt.setup != nil {
				tt.setup(env)
			}

			env.ExecuteWorkflow(
				workflow.GitHubAuthorResolutionWorkflow,
				workflow.GitHubAuthorResolutionInput{},
			)
			err := env.GetWorkflowError()
			if !tt.wantContinue {
				if err != nil {
					t.Fatalf("workflow error = %v", err)
				}
				return
			}
			if !sdkworkflow.IsContinueAsNewError(err) {
				t.Fatalf("workflow error = %T %v, want continue-as-new", err, err)
			}
		})
	}
}
