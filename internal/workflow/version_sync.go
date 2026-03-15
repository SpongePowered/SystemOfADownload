package workflow

import (
	"fmt"
	"time"

	"go.temporal.io/sdk/temporal"
	"go.temporal.io/sdk/workflow"

	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/domain"
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
type VersionSyncOutput struct {
	NewVersionsStored int
	NewVersions       []domain.VersionInfo
}

// VersionSyncWorkflow orchestrates fetching artifact versions from a Sonatype Nexus
// repository, persisting any that are not already in the database, launching a batch
// index workflow to process assets and commits, and then computing version ordering
// with schema-driven tag extraction.
func VersionSyncWorkflow(ctx workflow.Context, input VersionSyncInput) (*VersionSyncOutput, error) {
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

	var fetchResult activity.FetchVersionsOutput
	err := workflow.ExecuteActivity(ctx, activities.FetchVersions, activity.FetchVersionsInput{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
	}).Get(ctx, &fetchResult)
	if err != nil {
		return nil, fmt.Errorf("fetching versions: %w", err)
	}

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

	// Launch batch indexing for newly stored versions (assets + commits).
	if len(storeResult.NewVersions) > 0 {
		childOpts := workflow.ChildWorkflowOptions{
			WorkflowID: fmt.Sprintf("version-batch-index-%s-%s", input.GroupID, input.ArtifactID),
		}
		childCtx := workflow.WithChildOptions(ctx, childOpts)

		var batchResult int
		err = workflow.ExecuteChildWorkflow(childCtx, VersionBatchIndexWorkflow, VersionBatchIndexInput{
			Versions:   storeResult.NewVersions,
			WindowSize: versionBatchDefaultWindowSize,
		}).Get(ctx, &batchResult)
		if err != nil {
			return nil, fmt.Errorf("batch indexing versions: %w", err)
		}
	}

	// Compute version ordering and extract schema-driven tags.
	// This runs for ALL versions (not just new ones) since sort_order is relative.
	orderingOpts := workflow.ChildWorkflowOptions{
		WorkflowID: fmt.Sprintf("version-ordering-%s-%s", input.GroupID, input.ArtifactID),
	}
	orderingCtx := workflow.WithChildOptions(ctx, orderingOpts)

	var orderingResult VersionOrderingOutput
	err = workflow.ExecuteChildWorkflow(orderingCtx, VersionOrderingWorkflow, VersionOrderingInput{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
	}).Get(ctx, &orderingResult)
	if err != nil {
		return nil, fmt.Errorf("ordering versions: %w", err)
	}

	return &VersionSyncOutput{
		NewVersionsStored: len(storeResult.NewVersions),
		NewVersions:       storeResult.NewVersions,
	}, nil
}
