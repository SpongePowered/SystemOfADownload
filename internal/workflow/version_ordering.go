package workflow

import (
	"fmt"
	"slices"
	"time"

	"go.temporal.io/sdk/temporal"
	"go.temporal.io/sdk/workflow"

	"github.com/spongepowered/systemofadownload/internal/activity"
)

const (
	// orderingBatchSize controls how many versions are written per activity
	// call for ApplyVersionOrdering and StoreVersionTags. This keeps
	// individual transactions small and retries granular.
	orderingBatchSize = 500
)

// VersionOrderingInput is the input for the VersionOrderingWorkflow.
type VersionOrderingInput struct {
	GroupID    string
	ArtifactID string
	// MojangManifestURL is an optional URL to the Mojang version manifest.
	// If empty, the default Mojang manifest URL is used when the artifact's
	// schema has UseMojangManifest set to true.
	MojangManifestURL string
}

// VersionOrderingOutput is the result of the VersionOrderingWorkflow.
type VersionOrderingOutput struct {
	VersionsOrdered int
}

// VersionOrderingWorkflow computes and applies sort_order for all versions of
// an artifact. It loads the artifact's version schema from the database, then
// optionally fetches the Mojang version manifest, computes ordering, stores
// schema-extracted tags, and applies the sort order in batches.
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

	// Step 1: Fetch the version schema from the database.
	var schemaResult activity.FetchVersionSchemaOutput
	err := workflow.ExecuteActivity(ctx, orderingActivities.FetchVersionSchema, activity.FetchVersionSchemaInput{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
	}).Get(ctx, &schemaResult)
	if err != nil {
		return nil, fmt.Errorf("fetching version schema: %w", err)
	}

	// Step 2: Optionally fetch Mojang manifest.
	var manifestOrder map[string]int
	if schemaResult.Schema != nil && schemaResult.Schema.UseMojangManifest {
		var manifestResult activity.FetchMojangManifestOutput
		err := workflow.ExecuteActivity(ctx, orderingActivities.FetchMojangManifest, activity.FetchMojangManifestInput{
			ManifestURL: input.MojangManifestURL,
		}).Get(ctx, &manifestResult)
		if err != nil {
			return nil, fmt.Errorf("fetching Mojang manifest: %w", err)
		}
		manifestOrder = manifestResult.VersionOrder
	}

	// Step 3: Compute ordering (and extract tags if schema is present).
	var computeResult activity.ComputeVersionOrderingOutput
	err = workflow.ExecuteActivity(ctx, orderingActivities.ComputeVersionOrdering, activity.ComputeVersionOrderingInput{
		GroupID:       input.GroupID,
		ArtifactID:    input.ArtifactID,
		Schema:        schemaResult.Schema,
		ManifestOrder: manifestOrder,
	}).Get(ctx, &computeResult)
	if err != nil {
		return nil, fmt.Errorf("computing version ordering: %w", err)
	}

	if len(computeResult.Assignments) == 0 {
		return &VersionOrderingOutput{}, nil
	}

	// Step 4: Apply ordering in batches.
	for batch := range slices.Chunk(computeResult.Assignments, orderingBatchSize) {
		err = workflow.ExecuteActivity(ctx, orderingActivities.ApplyVersionOrdering, activity.ApplyVersionOrderingInput{
			Assignments: batch,
		}).Get(ctx, nil)
		if err != nil {
			return nil, fmt.Errorf("applying version ordering: %w", err)
		}
	}

	// Step 5: Store tags in batches.
	for batch := range slices.Chunk(computeResult.VersionTags, orderingBatchSize) {
		err = workflow.ExecuteActivity(ctx, orderingActivities.StoreVersionTags, activity.StoreVersionTagsInput{
			VersionTags: batch,
		}).Get(ctx, nil)
		if err != nil {
			return nil, fmt.Errorf("storing version tags: %w", err)
		}
	}

	return &VersionOrderingOutput{
		VersionsOrdered: len(computeResult.Assignments),
	}, nil
}
