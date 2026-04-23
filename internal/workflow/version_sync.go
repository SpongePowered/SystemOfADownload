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

// Typed search attribute keys upserted by VersionSyncWorkflow. Must be
// registered in the Temporal namespace before first use; see the runbook.
var (
	// VersionSyncSourceAttr labels the run with the source the workflow
	// actually dispatched to (search or metadata). One attribute per run —
	// each scheduled fire produces its own workflow execution with its own
	// value, so this is a faithful record of what ran.
	VersionSyncSourceAttr = temporal.NewSearchAttributeKeyKeyword("VersionSyncSource")
	// ArtifactCoordinateAttr is "<group>:<artifact>" for convenient
	// filtering in the Temporal UI.
	ArtifactCoordinateAttr = temporal.NewSearchAttributeKeyKeyword("ArtifactCoordinate")
)

// VersionSyncInput is the input payload for the VersionSyncWorkflow.
type VersionSyncInput struct {
	GroupID        string
	ArtifactID     string
	ForceReindex   bool
	ForceChangelog bool // skip indexing/enrichment, only re-compute changelogs
	// Source selects the version discovery strategy for FetchVersions.
	// Empty defaults to "search" for backward compatibility with any
	// in-flight scheduled runs at rollout.
	Source string
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
//
// Concurrency model: two schedules per artifact (2m fast / metadata, 1h full /
// search) fire this workflow with auto-generated unique workflow IDs per fire.
// The Go SDK v1.41.1 does not expose WorkflowIDConflictPolicy on
// ScheduleWorkflowAction, so cross-schedule runs are not serialized at the
// workflow-ID level; overlapping fires produce independent parent executions.
// Idempotency under concurrent fires relies on three things: child workflow
// IDs are scoped by parent run ID (so sibling parents can't collide on
// children), StoreNewVersions is ON CONFLICT DO NOTHING (so duplicate inserts
// are harmless), and the metadata path's asset-presence probe prevents ghost
// rows. Worker versioning (UseVersioning + AutoUpgrade in cmd/worker/main.go)
// means in-flight runs stay pinned to their start build ID — adding the
// Source field is zero-value-safe and does not require workflow.GetVersion
// because the workflow shape (activities called, order) is unchanged.
func VersionSyncWorkflow(ctx workflow.Context, input VersionSyncInput) (*VersionSyncOutput, error) {
	logger := workflow.GetLogger(ctx)
	source := input.Source
	if source == "" {
		source = activity.VersionSourceSearch
	}
	logger.Info("starting VersionSyncWorkflow",
		"groupID", input.GroupID, "artifactID", input.ArtifactID, "source", source)

	// Label the run with the dispatched source and artifact coordinate so
	// operators can filter by source in the Temporal UI and Grafana can
	// count actually-dispatched runs (not schedule intent — absorbed
	// attachments keep the winning run's attribute).
	if err := workflow.UpsertTypedSearchAttributes(ctx,
		VersionSyncSourceAttr.ValueSet(source),
		ArtifactCoordinateAttr.ValueSet(input.GroupID+":"+input.ArtifactID),
	); err != nil {
		logger.Warn("failed to upsert search attributes", "error", err)
	}

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

	// ForceChangelog: skip the entire fetch/store/index pipeline and only
	// re-compute changelogs for already-enriched versions.
	if input.ForceChangelog {
		return forceChangelogOnly(ctx, input)
	}

	var activities *activity.VersionSyncActivities

	// FetchVersions cost varies sharply by source:
	//   - search:   paginates N pages of component results, tens of seconds to minutes.
	//   - metadata: one GET plus O(new-candidates) asset probes, usually seconds.
	// Tight timeout on metadata keeps retry latency low when a worker is lost.
	fetchOpts := workflow.ActivityOptions{
		StartToCloseTimeout: 30 * time.Second,
		HeartbeatTimeout:    10 * time.Second,
		RetryPolicy: &temporal.RetryPolicy{
			InitialInterval:    2 * time.Second,
			BackoffCoefficient: 2.0,
			MaximumInterval:    30 * time.Second,
			MaximumAttempts:    3,
		},
	}
	if source == activity.VersionSourceSearch {
		fetchOpts.StartToCloseTimeout = 5 * time.Minute
		fetchOpts.HeartbeatTimeout = 30 * time.Second
	}
	fetchCtx := workflow.WithActivityOptions(ctx, fetchOpts)

	var fetchResult activity.FetchVersionsOutput
	err := workflow.ExecuteActivity(fetchCtx, activities.FetchVersions, activity.FetchVersionsInput{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
		Source:     source,
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
	//
	// Child workflow IDs are scoped by this parent's RunID so two concurrent
	// scheduled parents (fast + full firing within the same jitter window) do
	// not collide on child starts. Top-level orderings started via
	// PutArtifactSchema keep the unscoped `version-ordering-<g>-<a>` ID so
	// TERMINATE_EXISTING on schema change still works for operator-initiated
	// recomputes — the two scopes are intentionally disjoint.
	parentRunID := workflow.GetInfo(ctx).WorkflowExecution.RunID

	// Start indexing — for new versions, or all versions when force-reindexing.
	versionsToIndex := storeResult.NewVersions
	if input.ForceReindex {
		versionsToIndex = fetchResult.Versions
	}
	var indexFuture workflow.ChildWorkflowFuture
	if len(versionsToIndex) > 0 {
		indexOpts := workflow.ChildWorkflowOptions{
			WorkflowID: fmt.Sprintf("version-batch-index-%s-%s-%s", input.GroupID, input.ArtifactID, parentRunID),
		}
		indexCtx := workflow.WithChildOptions(ctx, indexOpts)
		indexFuture = workflow.ExecuteChildWorkflow(indexCtx, VersionBatchIndexWorkflow, VersionBatchIndexInput{
			Versions:   versionsToIndex,
			WindowSize: versionBatchDefaultWindowSize,
		})
	}

	// Start ordering (for ALL versions)
	orderingOpts := workflow.ChildWorkflowOptions{
		WorkflowID: fmt.Sprintf("version-ordering-%s-%s-%s", input.GroupID, input.ArtifactID, parentRunID),
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
		WorkflowID: fmt.Sprintf("commit-enrichment-%s-%s-%s", input.GroupID, input.ArtifactID, parentRunID),
	}
	enrichCtx := workflow.WithChildOptions(ctx, enrichOpts)

	var enrichResult CommitEnrichmentOutput
	err = workflow.ExecuteChildWorkflow(enrichCtx, CommitEnrichmentWorkflow, CommitEnrichmentInput{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
	}).Get(ctx, &enrichResult)
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

// forceChangelogOnly fetches already-enriched versions and re-computes only
// the changelog phase (Phase 2), skipping Sonatype fetch, JAR indexing, and
// commit enrichment entirely.
func forceChangelogOnly(ctx workflow.Context, input VersionSyncInput) (*VersionSyncOutput, error) {
	logger := workflow.GetLogger(ctx)

	var changelogActs *activity.ChangelogActivities

	var fetchResult activity.FetchVersionsForEnrichmentOutput
	err := workflow.ExecuteActivity(ctx, changelogActs.FetchEnrichedVersions, activity.FetchVersionsForEnrichmentInput{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
	}).Get(ctx, &fetchResult)
	if err != nil {
		return nil, fmt.Errorf("fetching enriched versions: %w", err)
	}

	logger.Info("ForceChangelog: fetched enriched versions", "count", len(fetchResult.Versions))

	if len(fetchResult.Versions) == 0 {
		return &VersionSyncOutput{}, nil
	}

	parentID := workflow.GetInfo(ctx).WorkflowExecution.ID
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

	logger.Info("ForceChangelog: complete", "changelogs", changelogs)
	return &VersionSyncOutput{}, nil
}
