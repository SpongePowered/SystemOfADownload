package workflow

import (
	"fmt"
	"time"

	"go.temporal.io/sdk/temporal"
	"go.temporal.io/sdk/workflow"

	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/domain"
)

// VersionIndexInput is the input for the VersionIndexWorkflow.
type VersionIndexInput struct {
	GroupID    string
	ArtifactID string
	Version    string
	TagRules   []domain.ArtifactTag
}

// VersionIndexOutput is the result of the VersionIndexWorkflow.
type VersionIndexOutput struct {
	AssetsStored int
	TagsCreated  int
	Recommended  bool
	CommitFound  bool
}

// VersionIndexWorkflow processes a single artifact version: fetches assets from
// Sonatype, stores them, applies tag rules, and extracts commit info from jars.
func VersionIndexWorkflow(ctx workflow.Context, input VersionIndexInput) (*VersionIndexOutput, error) {
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

	// Step 3: Build and store tags
	var tagResult activity.BuildAndStoreTagsOutput
	if len(input.TagRules) > 0 {
		err = workflow.ExecuteActivity(ctx, indexActivities.BuildAndStoreTags, activity.BuildAndStoreTagsInput{
			GroupID:    input.GroupID,
			ArtifactID: input.ArtifactID,
			Version:    input.Version,
			Assets:     fetchResult.Assets,
			TagRules:   input.TagRules,
		}).Get(ctx, &tagResult)
		if err != nil {
			return nil, fmt.Errorf("building and storing tags: %w", err)
		}
	}

	// Step 4: Identify jar candidates for commit extraction
	var inspectResult activity.InspectJarsForCommitsOutput
	err = workflow.ExecuteActivity(ctx, indexActivities.InspectJarsForCommits, activity.InspectJarsForCommitsInput{
		Assets: fetchResult.Assets,
	}).Get(ctx, &inspectResult)
	if err != nil {
		return nil, fmt.Errorf("inspecting jars: %w", err)
	}

	output := &VersionIndexOutput{
		AssetsStored: storeResult.StoredCount,
		TagsCreated:  tagResult.TagsCreated,
		Recommended:  tagResult.Recommended,
	}

	// Step 5: Launch ExtractCommitBatch if jar candidates found
	if len(inspectResult.Candidates) > 0 {
		childOpts := workflow.ChildWorkflowOptions{
			WorkflowID: fmt.Sprintf("%s/extract-commits", workflow.GetInfo(ctx).WorkflowExecution.ID),
			RetryPolicy: &temporal.RetryPolicy{
				MaximumAttempts: 2,
			},
		}
		childCtx := workflow.WithChildOptions(ctx, childOpts)

		var commitsExtracted int
		err = workflow.ExecuteChildWorkflow(childCtx, ExtractCommitBatchWorkflow, ExtractCommitBatchInput{
			GroupID:    input.GroupID,
			ArtifactID: input.ArtifactID,
			Version:    input.Version,
			Candidates: inspectResult.Candidates,
			WindowSize: 3,
		}).Get(ctx, &commitsExtracted)
		if err != nil {
			return nil, fmt.Errorf("extracting commits: %w", err)
		}
		output.CommitFound = commitsExtracted > 0
	}

	// Signal parent workflow about completion
	parent := workflow.GetInfo(ctx).ParentWorkflowExecution
	if parent != nil {
		signaled := workflow.SignalExternalWorkflow(ctx, parent.ID, "", versionBatchCompletionSignalName, input.Version)
		if err := signaled.Get(ctx, nil); err != nil {
			return nil, fmt.Errorf("signaling parent: %w", err)
		}
	}

	return output, nil
}
