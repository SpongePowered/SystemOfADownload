package workflow

import (
	"fmt"
	"time"

	"go.temporal.io/sdk/temporal"
	"go.temporal.io/sdk/workflow"

	"github.com/spongepowered/systemofadownload/internal/activity"
)

// VersionIndexInput is the input for the VersionIndexWorkflow.
type VersionIndexInput struct {
	GroupID    string
	ArtifactID string
	Version    string
}

// VersionIndexOutput is the result of the VersionIndexWorkflow.
type VersionIndexOutput struct {
	AssetsStored int
	CommitFound  bool
}

// VersionIndexWorkflow processes a single artifact version: fetches assets from
// Sonatype, stores them, and extracts commit info from jars.
// Tags are handled separately by VersionOrderingWorkflow using schema-driven extraction.
func VersionIndexWorkflow(ctx workflow.Context, input VersionIndexInput) (*VersionIndexOutput, error) {
	output, err := versionIndexWork(ctx, input)

	// Always signal the parent, even on failure. Without this, the batch
	// workflow's currentRecords map never clears for this version, and the
	// sliding window blocks forever.
	parent := workflow.GetInfo(ctx).ParentWorkflowExecution
	if parent != nil {
		signaled := workflow.SignalExternalWorkflow(ctx, parent.ID, "", versionBatchCompletionSignalName, input.Version)
		_ = signaled.Get(ctx, nil) // best-effort; don't mask the original error
	}

	return output, err
}

func versionIndexWork(ctx workflow.Context, input VersionIndexInput) (*VersionIndexOutput, error) {
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

	var indexActivities *activity.VersionIndexActivities

	// Step 1: Fetch assets from Sonatype
	var fetchResult activity.FetchVersionAssetsOutput
	err := workflow.ExecuteActivity(ctx, indexActivities.FetchVersionAssets, activity.FetchVersionAssetsInput{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
		Version:    input.Version,
	}).Get(ctx, &fetchResult)
	if err != nil {
		return nil, fmt.Errorf("fetching version assets: %w", err)
	}

	if len(fetchResult.Assets) == 0 {
		return &VersionIndexOutput{}, nil
	}

	// Step 2: Store assets
	var storeResult activity.StoreVersionAssetsOutput
	err = workflow.ExecuteActivity(ctx, indexActivities.StoreVersionAssets, activity.StoreVersionAssetsInput{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
		Version:    input.Version,
		Assets:     fetchResult.Assets,
	}).Get(ctx, &storeResult)
	if err != nil {
		return nil, fmt.Errorf("storing version assets: %w", err)
	}

	// Step 3: Pick the best jar candidate for commit extraction.
	// This is pure computation (no I/O) so it's called directly in the
	// workflow, not as an activity. Saves 3 history events per version.
	candidate := activity.PickBestJarCandidate(fetchResult.Assets)

	output := &VersionIndexOutput{
		AssetsStored: storeResult.StoredCount,
	}

	// Step 4: Extract commit from the best jar candidate (if any)
	if candidate != nil {

		extractOpts := workflow.ActivityOptions{
			StartToCloseTimeout: 5 * time.Minute,
			RetryPolicy: &temporal.RetryPolicy{
				InitialInterval:    2 * time.Second,
				BackoffCoefficient: 2.0,
				MaximumInterval:    time.Minute,
				MaximumAttempts:    3,
			},
		}
		extractCtx := workflow.WithActivityOptions(ctx, extractOpts)

		var extractResult activity.ExtractCommitFromJarOutput
		err = workflow.ExecuteActivity(extractCtx, indexActivities.ExtractCommitFromJar, activity.ExtractCommitFromJarInput{
			DownloadURL: candidate.DownloadURL,
		}).Get(ctx, &extractResult)
		if err != nil {
			return nil, fmt.Errorf("extracting commit from jar: %w", err)
		}

		if extractResult.CommitInfo != nil {
			err = workflow.ExecuteActivity(ctx, indexActivities.StoreCommitInfo, activity.StoreCommitInfoInput{
				GroupID:    input.GroupID,
				ArtifactID: input.ArtifactID,
				Version:    input.Version,
				CommitInfo: *extractResult.CommitInfo,
			}).Get(ctx, nil)
			if err != nil {
				return nil, fmt.Errorf("storing commit info: %w", err)
			}
			output.CommitFound = true
		}
	}

	return output, nil
}
