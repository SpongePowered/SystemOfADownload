package workflow

import (
	"fmt"
	"time"

	"go.temporal.io/sdk/temporal"
	"go.temporal.io/sdk/workflow"

	"github.com/spongepowered/systemofadownload/internal/activity"
)

const (
	// VersionSyncTaskQueue is the Temporal task queue name for version sync workflows.
	VersionSyncTaskQueue = "version-sync"
)

// VersionSyncInput is the input payload for the VersionSyncWorkflow.
type VersionSyncInput struct {
	GroupID    string
	ArtifactID string
}

// VersionSyncOutput is the result of the VersionSyncWorkflow.
// Only contains a summary to keep the completion event payload small.
type VersionSyncOutput struct {
	NewVersionsStored int
	NewestVersion     string // highest sort_order among new versions (empty if none)
	OldestVersion     string // lowest sort_order among new versions (empty if none)
}

// VersionSyncWorkflow orchestrates fetching artifact versions from a Sonatype Nexus
// repository, persisting any that are not already in the database, launching a batch
// index workflow to process assets and commits, and then computing version ordering
// with schema-driven tag extraction.
func VersionSyncWorkflow(ctx workflow.Context, input VersionSyncInput) (*VersionSyncOutput, error) {
	logger := workflow.GetLogger(ctx)
	logger.Info("starting VersionSyncWorkflow", "groupID", input.GroupID, "artifactID", input.ArtifactID)

	activityOptions := workflow.ActivityOptions{
		StartToCloseTimeout: 30 * time.Second,
		RetryPolicy: &temporal.RetryPolicy{
			InitialInterval:    time.Second,
			BackoffCoefficient: 2.0,
			MaximumInterval:    30 * time.Second,
			MaximumAttempts:    3,
		},
	}
	ctx = workflow.WithActivityOptions(ctx, activityOptions)

	var activities *activity.VersionSyncActivities

	// FetchVersions paginates through the Sonatype REST API and can take a while.
	fetchOpts := workflow.ActivityOptions{
		StartToCloseTimeout: 5 * time.Minute,
		HeartbeatTimeout:    30 * time.Second,
		RetryPolicy: &temporal.RetryPolicy{
			InitialInterval:    2 * time.Second,
			BackoffCoefficient: 2.0,
			MaximumInterval:    30 * time.Second,
			MaximumAttempts:    3,
		},
	}
	fetchCtx := workflow.WithActivityOptions(ctx, fetchOpts)

	var fetchResult activity.FetchVersionsOutput
	err := workflow.ExecuteActivity(fetchCtx, activities.FetchVersions, activity.FetchVersionsInput{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
	}).Get(ctx, &fetchResult)
	if err != nil {
		return nil, fmt.Errorf("fetching versions: %w", err)
	}

	logger.Info("fetched versions", "count", len(fetchResult.Versions))

	if len(fetchResult.Versions) == 0 {
		return &VersionSyncOutput{}, nil
	}

	var storeResult activity.StoreNewVersionsOutput
	err = workflow.ExecuteActivity(ctx, activities.StoreNewVersions, activity.StoreNewVersionsInput{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
		Versions:   fetchResult.Versions,
	}).Get(ctx, &storeResult)
	if err != nil {
		return nil, fmt.Errorf("storing new versions: %w", err)
	}

	logger.Info("stored new versions", "newCount", len(storeResult.NewVersions))

	// Launch batch indexing and version ordering in parallel.
	// Indexing needs version rows (from StoreNewVersions) but not sort_order.
	// Ordering needs version rows but not assets or commits.
	// Both can run concurrently.

	// Start indexing (only for new versions)
	var indexFuture workflow.ChildWorkflowFuture
	if len(storeResult.NewVersions) > 0 {
		indexOpts := workflow.ChildWorkflowOptions{
			WorkflowID: fmt.Sprintf("version-batch-index-%s-%s", input.GroupID, input.ArtifactID),
		}
		indexCtx := workflow.WithChildOptions(ctx, indexOpts)
		indexFuture = workflow.ExecuteChildWorkflow(indexCtx, VersionBatchIndexWorkflow, VersionBatchIndexInput{
			Versions:   storeResult.NewVersions,
			WindowSize: versionBatchDefaultWindowSize,
		})
	}

	// Start ordering (for ALL versions)
	orderingOpts := workflow.ChildWorkflowOptions{
		WorkflowID: fmt.Sprintf("version-ordering-%s-%s", input.GroupID, input.ArtifactID),
	}
	orderingCtx := workflow.WithChildOptions(ctx, orderingOpts)
	orderingFuture := workflow.ExecuteChildWorkflow(orderingCtx, VersionOrderingWorkflow, VersionOrderingInput{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
	})

	// Wait for both to complete
	if indexFuture != nil {
		var batchResult int
		if err := indexFuture.Get(ctx, &batchResult); err != nil {
			return nil, fmt.Errorf("batch indexing versions: %w", err)
		}
	}

	var orderingResult VersionOrderingOutput
	if err := orderingFuture.Get(ctx, &orderingResult); err != nil {
		return nil, fmt.Errorf("ordering versions: %w", err)
	}

	// Enrich commit details and compute changelogs.
	enrichOpts := workflow.ChildWorkflowOptions{
		WorkflowID: fmt.Sprintf("commit-enrichment-%s-%s", input.GroupID, input.ArtifactID),
	}
	enrichCtx := workflow.WithChildOptions(ctx, enrichOpts)

	var enrichResult CommitEnrichmentOutput
	err = workflow.ExecuteChildWorkflow(enrichCtx, CommitEnrichmentWorkflow, CommitEnrichmentInput(input)).Get(ctx, &enrichResult)
	if err != nil {
		return nil, fmt.Errorf("enriching commits: %w", err)
	}

	output := &VersionSyncOutput{
		NewVersionsStored: len(storeResult.NewVersions),
	}
	if len(storeResult.NewVersions) > 0 {
		output.NewestVersion = storeResult.NewVersions[len(storeResult.NewVersions)-1].Version
		output.OldestVersion = storeResult.NewVersions[0].Version
	}
	return output, nil
}
