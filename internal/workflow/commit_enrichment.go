package workflow

import (
	"fmt"
	"time"

	"go.temporal.io/sdk/temporal"
	"go.temporal.io/sdk/workflow"

	"github.com/spongepowered/systemofadownload/internal/activity"
)

// CommitEnrichmentInput is the input for the CommitEnrichmentWorkflow.
type CommitEnrichmentInput struct {
	GroupID    string
	ArtifactID string
}

// CommitEnrichmentOutput is the result of the CommitEnrichmentWorkflow.
type CommitEnrichmentOutput struct {
	VersionsEnriched   int
	ChangelogsComputed int
}

// CommitEnrichmentWorkflow is the entry point for enriching version commits
// with full git details and computing changelogs between consecutive versions.
//
// Phase 1: Enrich all versions in parallel (sliding window)
// Phase 2: Compute changelogs sequentially (window size 1)
func CommitEnrichmentWorkflow(ctx workflow.Context, input CommitEnrichmentInput) (*CommitEnrichmentOutput, error) {
	activityOpts := workflow.ActivityOptions{
		StartToCloseTimeout: 30 * time.Second,
		RetryPolicy: &temporal.RetryPolicy{
			InitialInterval:    time.Second,
			BackoffCoefficient: 2.0,
			MaximumInterval:    30 * time.Second,
			MaximumAttempts:    3,
		},
	}
	ctx = workflow.WithActivityOptions(ctx, activityOpts)

	var changelogActs *activity.ChangelogActivities

	// Fetch versions that need enrichment
	var fetchResult activity.FetchVersionsForEnrichmentOutput
	err := workflow.ExecuteActivity(ctx, changelogActs.FetchVersionsForEnrichment, activity.FetchVersionsForEnrichmentInput{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
	}).Get(ctx, &fetchResult)
	if err != nil {
		return nil, fmt.Errorf("fetching versions for enrichment: %w", err)
	}

	if len(fetchResult.Versions) == 0 {
		return &CommitEnrichmentOutput{}, nil
	}

	parentID := workflow.GetInfo(ctx).WorkflowExecution.ID

	// Phase 1: Enrich all versions (parallel, sliding window)
	enrichOpts := workflow.ChildWorkflowOptions{
		WorkflowID: fmt.Sprintf("%s/enrichment-batch", parentID),
	}
	enrichCtx := workflow.WithChildOptions(ctx, enrichOpts)

	var enriched int
	err = workflow.ExecuteChildWorkflow(enrichCtx, EnrichmentBatchWorkflow, EnrichmentBatchInput{
		Versions:        fetchResult.Versions,
		GitRepositories: fetchResult.GitRepositories,
		WindowSize:      enrichmentBatchDefaultWindowSize,
	}).Get(ctx, &enriched)
	if err != nil {
		return nil, fmt.Errorf("enrichment batch: %w", err)
	}

	// Phase 2: Compute changelogs (sequential, window size 1)
	changelogOpts := workflow.ChildWorkflowOptions{
		WorkflowID: fmt.Sprintf("%s/changelog-batch", parentID),
	}
	changelogCtx := workflow.WithChildOptions(ctx, changelogOpts)

	var changelogs int
	err = workflow.ExecuteChildWorkflow(changelogCtx, ChangelogBatchWorkflow, ChangelogBatchInput{
		Versions:        fetchResult.Versions,
		GitRepositories: fetchResult.GitRepositories,
		WindowSize:      changelogBatchDefaultWindowSize,
	}).Get(ctx, &changelogs)
	if err != nil {
		return nil, fmt.Errorf("changelog batch: %w", err)
	}

	return &CommitEnrichmentOutput{
		VersionsEnriched:   enriched,
		ChangelogsComputed: changelogs,
	}, nil
}
