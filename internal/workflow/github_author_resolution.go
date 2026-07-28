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

	var versionIDs []int64
	err := workflow.ExecuteActivity(
		fetchCtx,
		activities.FetchVersionsNeedingAuthorResolution,
		activity.FetchVersionsNeedingAuthorResolutionInput{
			BeforeID: input.BeforeID,
			PageSize: githubAuthorResolutionPageSize,
		},
	).Get(ctx, &versionIDs)
	if err != nil {
		return fmt.Errorf("fetching GitHub author resolution page: %w", err)
	}
	if len(versionIDs) == 0 {
		return nil
	}

	resolveCtx := workflow.WithActivityOptions(ctx, workflow.ActivityOptions{
		StartToCloseTimeout:    time.Minute,
		ScheduleToCloseTimeout: 10 * time.Minute,
		HeartbeatTimeout:       30 * time.Second,
		RetryPolicy: &temporal.RetryPolicy{
			MaximumAttempts: 5,
		},
	})

	var resolved activity.ResolveGitHubAuthorsBatchOutput
	err = workflow.ExecuteActivity(
		resolveCtx,
		activities.ResolveGitHubAuthorsBatch,
		activity.ResolveGitHubAuthorsBatchInput{VersionIDs: versionIDs},
	).Get(ctx, &resolved)
	if err != nil {
		return fmt.Errorf("resolving GitHub authors: %w", err)
	}
	workflow.GetLogger(ctx).Info("resolved GitHub author page",
		"versionsStamped", resolved.VersionsStamped,
		"authorsResolved", resolved.AuthorsResolved,
		"authorsUnresolved", resolved.AuthorsUnresolved,
	)

	// Resolved rows leave the unresolved index, but skipped ones do not, so the
	// cursor is what guarantees the scan keeps moving toward older versions.
	nextBeforeID := versionIDs[len(versionIDs)-1]
	return workflow.NewContinueAsNewError(ctx, GitHubAuthorResolutionWorkflow, GitHubAuthorResolutionInput{
		BeforeID: &nextBeforeID,
	})
}
