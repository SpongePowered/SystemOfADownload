package workflow

import (
	"fmt"
	"time"

	"go.temporal.io/sdk/temporal"
	"go.temporal.io/sdk/workflow"

	"github.com/spongepowered/systemofadownload/internal/activity"
)

const (
	// GitHubAuthorResolutionScheduleID is the singleton schedule identifier.
	GitHubAuthorResolutionScheduleID = "github-author-resolution"
	githubAuthorResolutionPageSize   = 50
)

// GitHubAuthorResolutionInput carries the keyset cursor across continue-as-new runs.
type GitHubAuthorResolutionInput struct {
	BeforeID *int64
}

// GitHubAuthorResolutionWorkflow drains unresolved enriched versions newest-first.
func GitHubAuthorResolutionWorkflow(
	ctx workflow.Context,
	input GitHubAuthorResolutionInput,
) error {
	var activities *activity.GitHubAuthorResolutionActivities
	fetchCtx := workflow.WithActivityOptions(ctx, workflow.ActivityOptions{
		StartToCloseTimeout: 30 * time.Second,
		RetryPolicy: &temporal.RetryPolicy{
			MaximumAttempts: 3,
		},
	})

	var page activity.FetchGitHubAuthorResolutionPageOutput
	err := workflow.ExecuteActivity(
		fetchCtx,
		activities.FetchGitHubAuthorResolutionPage,
		activity.FetchGitHubAuthorResolutionPageInput{
			BeforeID: input.BeforeID,
			PageSize: githubAuthorResolutionPageSize,
		},
	).Get(ctx, &page)
	if err != nil {
		return fmt.Errorf("fetching GitHub author resolution page: %w", err)
	}
	if len(page.VersionIDs) == 0 {
		return nil
	}

	resolveCtx := workflow.WithActivityOptions(ctx, workflow.ActivityOptions{
		StartToCloseTimeout: 10 * time.Minute,
		HeartbeatTimeout:    2 * time.Minute,
		RetryPolicy: &temporal.RetryPolicy{
			MaximumAttempts: 3,
		},
	})

	err = workflow.ExecuteActivity(
		resolveCtx,
		activities.ResolveGitHubAuthorsBatch,
		activity.ResolveGitHubAuthorsBatchInput{VersionIDs: page.VersionIDs},
	).Get(ctx, nil)
	if err != nil {
		return fmt.Errorf("resolving GitHub authors: %w", err)
	}

	return workflow.NewContinueAsNewError(ctx, GitHubAuthorResolutionWorkflow, GitHubAuthorResolutionInput{
		BeforeID: page.NextBeforeID,
	})
}
