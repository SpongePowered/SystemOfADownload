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
	VersionsFound int
	Versions      []domain.VersionInfo
}

// VersionSyncWorkflow orchestrates fetching artifact versions from a Sonatype Nexus repository.
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

	return &VersionSyncOutput{
		VersionsFound: len(fetchResult.Versions),
		Versions:      fetchResult.Versions,
	}, nil
}
