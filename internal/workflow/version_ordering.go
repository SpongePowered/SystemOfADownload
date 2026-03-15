package workflow

import (
	"fmt"
	"time"

	"go.temporal.io/sdk/temporal"
	"go.temporal.io/sdk/workflow"

	"github.com/spongepowered/systemofadownload/internal/activity"
)

// VersionOrderingInput is the input for the VersionOrderingWorkflow.
type VersionOrderingInput struct {
	GroupID    string
	ArtifactID string
	// MojangManifestURL is an optional URL to the Mojang version manifest.
	// If set, weekly snapshots and Minecraft versions are ordered using the manifest.
	// If empty, the default Mojang manifest URL is used when UseMojangManifest is true.
	MojangManifestURL string
	// UseMojangManifest controls whether to fetch the Mojang version manifest
	// for ordering. Set to true for Minecraft-related artifacts.
	UseMojangManifest bool
}

// VersionOrderingOutput is the result of the VersionOrderingWorkflow.
type VersionOrderingOutput struct {
	VersionsOrdered int
}

// VersionOrderingWorkflow computes and applies sort_order for all versions of
// an artifact. It optionally uses the Mojang version manifest to correctly
// place weekly snapshots and Minecraft versions.
func VersionOrderingWorkflow(ctx workflow.Context, input VersionOrderingInput) (*VersionOrderingOutput, error) {
	activityOpts := workflow.ActivityOptions{
		StartToCloseTimeout: time.Minute,
		RetryPolicy: &temporal.RetryPolicy{
			InitialInterval:    time.Second,
			BackoffCoefficient: 2.0,
			MaximumInterval:    30 * time.Second,
			MaximumAttempts:    3,
		},
	}
	ctx = workflow.WithActivityOptions(ctx, activityOpts)

	var orderingActivities *activity.VersionOrderingActivities

	// Step 1: Optionally fetch Mojang manifest.
	var manifestOrder map[string]int
	if input.UseMojangManifest {
		var manifestResult activity.FetchMojangManifestOutput
		err := workflow.ExecuteActivity(ctx, orderingActivities.FetchMojangManifest, activity.FetchMojangManifestInput{
			ManifestURL: input.MojangManifestURL,
		}).Get(ctx, &manifestResult)
		if err != nil {
			return nil, fmt.Errorf("fetching Mojang manifest: %w", err)
		}
		manifestOrder = manifestResult.VersionOrder
	}

	// Step 2: Compute ordering.
	var computeResult activity.ComputeVersionOrderingOutput
	err := workflow.ExecuteActivity(ctx, orderingActivities.ComputeVersionOrdering, activity.ComputeVersionOrderingInput{
		GroupID:       input.GroupID,
		ArtifactID:    input.ArtifactID,
		ManifestOrder: manifestOrder,
	}).Get(ctx, &computeResult)
	if err != nil {
		return nil, fmt.Errorf("computing version ordering: %w", err)
	}

	if len(computeResult.Assignments) == 0 {
		return &VersionOrderingOutput{}, nil
	}

	// Step 3: Apply ordering.
	err = workflow.ExecuteActivity(ctx, orderingActivities.ApplyVersionOrdering, activity.ApplyVersionOrderingInput{
		Assignments: computeResult.Assignments,
	}).Get(ctx, nil)
	if err != nil {
		return nil, fmt.Errorf("applying version ordering: %w", err)
	}

	return &VersionOrderingOutput{
		VersionsOrdered: len(computeResult.Assignments),
	}, nil
}
