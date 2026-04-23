package activity

import (
	"context"
	"fmt"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/metric"
	"go.temporal.io/sdk/activity"
	"go.temporal.io/sdk/temporal"

	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/repository"
	"github.com/spongepowered/systemofadownload/internal/sonatype"
)

// Version sources recognized by FetchVersions. Empty string defaults to
// VersionSourceSearch for backward compatibility with any scheduled runs
// in flight at deploy time.
const (
	VersionSourceSearch   = "search"
	VersionSourceMetadata = "metadata"
)

var syncMeter = otel.Meter("soad.version_sync")

// Counters/gauges emitted by this activity file. Labeled by the *dispatched*
// source — not the schedule's intended source — so a USE_EXISTING-attached
// run reports the source the winning run actually executed with.
var (
	fetchedTotal, _      = syncMeter.Int64Counter("soad.version_sync.fetched_total")
	probeSkippedTotal, _ = syncMeter.Int64Counter("soad.version_sync.probe_skipped_total")
)

// VersionSyncActivities holds the dependencies for version sync activities.
type VersionSyncActivities struct {
	SonatypeClient sonatype.Client
	Repo           repository.Repository
}

// FetchVersionsInput is the input for the FetchVersions activity.
type FetchVersionsInput struct {
	GroupID    string
	ArtifactID string
	// Source selects the version discovery strategy. Empty → VersionSourceSearch.
	Source string
}

// FetchVersionsOutput is the output of the FetchVersions activity.
type FetchVersionsOutput struct {
	Versions []domain.VersionInfo
}

// FetchVersions retrieves all version info for an artifact from Sonatype.
//
// With Source == VersionSourceSearch (or empty), it paginates the REST
// component search — the authoritative list of indexed components. Retries
// restart pagination from scratch; the API is idempotent and fast.
//
// With Source == VersionSourceMetadata, it fetches maven-metadata.xml (a
// single GET) and then gates the candidate list by probing SearchAssets for
// each version the database does not yet know about. Versions whose assets
// are not yet indexed — or whose only assets live in a denied hosted repo —
// are dropped. This protects against ghost-version rows that would never
// self-heal because VersionIndexWorkflow silently returns on empty asset
// lists.
func (a *VersionSyncActivities) FetchVersions(ctx context.Context, input FetchVersionsInput) (*FetchVersionsOutput, error) {
	source := input.Source
	if source == "" {
		source = VersionSourceSearch
	}

	sourceAttr := attribute.String("source", source)

	switch source {
	case VersionSourceSearch:
		versions, err := a.fetchVersionsSearch(ctx, input.GroupID, input.ArtifactID)
		recordFetchResult(ctx, sourceAttr, err)
		if err != nil {
			return nil, err
		}
		return &FetchVersionsOutput{Versions: versions}, nil
	case VersionSourceMetadata:
		versions, err := a.fetchVersionsMetadata(ctx, input.GroupID, input.ArtifactID)
		recordFetchResult(ctx, sourceAttr, err)
		if err != nil {
			return nil, err
		}
		return &FetchVersionsOutput{Versions: versions}, nil
	default:
		return nil, temporal.NewNonRetryableApplicationError(
			fmt.Sprintf("unknown version source %q", input.Source),
			"InvalidInput",
			nil,
		)
	}
}

func recordFetchResult(ctx context.Context, sourceAttr attribute.KeyValue, err error) {
	status := "success"
	if err != nil {
		status = "failure"
	}
	fetchedTotal.Add(ctx, 1, metric.WithAttributes(sourceAttr, attribute.String("status", status)))
}

func (a *VersionSyncActivities) fetchVersionsSearch(ctx context.Context, groupID, artifactID string) ([]domain.VersionInfo, error) {
	// Heartbeat just the count on each page (~8 bytes vs ~300KB for the full list).
	fetchClient := a.SonatypeClient
	if sc, ok := a.SonatypeClient.(*sonatype.HTTPClient); ok {
		fetchClient = sc.WithProgress(func(versions []domain.VersionInfo, _ string) {
			activity.RecordHeartbeat(ctx, len(versions))
		})
	}
	versions, err := fetchClient.FetchVersions(ctx, groupID, artifactID)
	if err != nil {
		return nil, fmt.Errorf("fetching versions: %w", err)
	}
	return versions, nil
}

// metadataInitialSeedProbeCap is the maximum number of asset probes the
// metadata path will run on a single tick. Beyond this, we defer discovery
// to the next fast tick (for steady-state) or the hourly full schedule (for
// initial-seed / large backfills). 50 is generous: at ~200ms per probe it
// fits comfortably in the 30s activity StartToCloseTimeout while leaving
// headroom for maven-metadata.xml fetch and heartbeats.
const metadataInitialSeedProbeCap = 50

// fetchVersionsMetadata downloads maven-metadata.xml, then probes each
// candidate-new version with VersionHasAssets. Candidates are the subset of
// the metadata list that the database does not yet record — probing the
// full metadata list on every tick would be O(all-versions) HTTP calls and
// is unnecessary since already-persisted versions have already been gated.
//
// Two backfill guards:
//
//   - If the DB has no rows for this artifact, return nil immediately. The
//     hourly full schedule (search source) handles initial seeding — REST
//     search is ghost-row-safe by construction (Sonatype indexes components
//     after assets), and probing every metadata candidate serially on a 30s
//     budget would blow the activity timeout for any non-trivial artifact.
//   - If the new-candidate count exceeds metadataInitialSeedProbeCap, skip
//     probing and return nil for this tick. Again the full schedule
//     handles large backfills.
func (a *VersionSyncActivities) fetchVersionsMetadata(ctx context.Context, groupID, artifactID string) ([]domain.VersionInfo, error) {
	activity.RecordHeartbeat(ctx, "fetching metadata")

	candidates, err := a.SonatypeClient.FetchVersionsFromMetadata(ctx, groupID, artifactID)
	if err != nil {
		return nil, fmt.Errorf("fetching maven-metadata.xml: %w", err)
	}
	if len(candidates) == 0 {
		return nil, nil
	}

	// Narrow to versions the DB doesn't already know about. Probing an
	// already-stored version adds no signal (we won't insert it anyway).
	existing, err := a.existingVersionSet(ctx, groupID, artifactID)
	if err != nil {
		return nil, err
	}

	// Initial seed: DB is empty. Defer to the hourly full schedule.
	if len(existing) == 0 {
		return nil, nil
	}

	// Count unknown candidates up front to decide whether to probe or defer.
	var unknown int
	for _, v := range candidates {
		if _, known := existing[v.Version]; !known {
			unknown++
		}
	}
	if unknown > metadataInitialSeedProbeCap {
		return nil, nil
	}

	accepted := make([]domain.VersionInfo, 0, len(candidates))
	probed := 0
	for _, v := range candidates {
		if _, known := existing[v.Version]; known {
			// Already in DB — pass through so ForceReindex callers see the
			// full version list. StoreNewVersions filters duplicates.
			accepted = append(accepted, v)
			continue
		}
		probed++
		activity.RecordHeartbeat(ctx, fmt.Sprintf("probing %d/%d", probed, unknown))
		ok, err := a.SonatypeClient.VersionHasAssets(ctx, groupID, artifactID, v.Version)
		if err != nil {
			return nil, fmt.Errorf("probing assets for %s: %w", v.Version, err)
		}
		if !ok {
			probeSkippedTotal.Add(ctx, 1, metric.WithAttributes(
				attribute.String("source", VersionSourceMetadata),
				attribute.String("reason", "no_assets"),
			))
			continue
		}
		accepted = append(accepted, v)
	}

	return accepted, nil
}

// existingVersionSet returns the set of already-stored version strings for an
// artifact. Returns an empty set when the artifact has no rows yet (a newly
// registered artifact).
func (a *VersionSyncActivities) existingVersionSet(ctx context.Context, groupID, artifactID string) (map[string]struct{}, error) {
	artifact, err := a.Repo.GetArtifactByGroupAndId(ctx, db.GetArtifactByGroupAndIdParams{
		GroupID:    groupID,
		ArtifactID: artifactID,
	})
	if err != nil {
		return nil, fmt.Errorf("looking up artifact %s:%s: %w", groupID, artifactID, err)
	}
	existing := make(map[string]struct{})
	for offset := int32(0); ; offset += versionPageSize {
		page, err := a.Repo.ListArtifactVersionStringsByArtifactID(ctx, db.ListArtifactVersionStringsByArtifactIDParams{
			ArtifactID: artifact.ID,
			Limit:      versionPageSize,
			Offset:     offset,
		})
		if err != nil {
			return nil, fmt.Errorf("listing existing versions (offset %d): %w", offset, err)
		}
		for _, v := range page {
			existing[v] = struct{}{}
		}
		if int32(len(page)) < versionPageSize {
			break
		}
	}
	return existing, nil
}

// StoreNewVersionsInput is the input for the StoreNewVersions activity.
type StoreNewVersionsInput struct {
	GroupID    string
	ArtifactID string
	Versions   []domain.VersionInfo
}

// StoreNewVersionsOutput is the output of the StoreNewVersions activity.
type StoreNewVersionsOutput struct {
	NewVersions []domain.VersionInfo
}

const versionPageSize int32 = 100

// StoreNewVersions compares fetched versions against the database, stores any
// that are new, and returns only the newly created versions.
func (a *VersionSyncActivities) StoreNewVersions(ctx context.Context, input StoreNewVersionsInput) (*StoreNewVersionsOutput, error) {
	if len(input.Versions) == 0 {
		return &StoreNewVersionsOutput{}, nil
	}

	// Look up the artifact's internal ID.
	artifact, err := a.Repo.GetArtifactByGroupAndId(ctx, db.GetArtifactByGroupAndIdParams{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
	})
	if err != nil {
		return nil, fmt.Errorf("looking up artifact %s:%s: %w", input.GroupID, input.ArtifactID, err)
	}

	// Page through existing versions to build a set for comparison.
	existing := make(map[string]struct{})
	for offset := int32(0); ; offset += versionPageSize {
		page, err := a.Repo.ListArtifactVersionStringsByArtifactID(ctx, db.ListArtifactVersionStringsByArtifactIDParams{
			ArtifactID: artifact.ID,
			Limit:      versionPageSize,
			Offset:     offset,
		})
		if err != nil {
			return nil, fmt.Errorf("listing existing versions (offset %d): %w", offset, err)
		}
		for _, v := range page {
			existing[v] = struct{}{}
		}
		if int32(len(page)) < versionPageSize {
			break
		}
	}

	// Filter to only new versions.
	var newVersions []domain.VersionInfo
	for _, v := range input.Versions {
		if _, found := existing[v.Version]; !found {
			newVersions = append(newVersions, v)
		}
	}

	if len(newVersions) == 0 {
		return &StoreNewVersionsOutput{}, nil
	}

	// Store new versions within a transaction. InsertNewArtifactVersion uses
	// ON CONFLICT DO NOTHING so a concurrent sync run that races in between
	// our pre-filter and this insert can't cause a unique-constraint failure
	// that would roll back the whole batch — nor can it overwrite sort_order
	// or commit_body written by a later enrichment/ordering stage (the full
	// CreateArtifactVersion upsert is reserved for those paths).
	err = a.Repo.WithTx(ctx, func(tx repository.Tx) error {
		for _, v := range newVersions {
			err := tx.InsertNewArtifactVersion(ctx, db.InsertNewArtifactVersionParams{
				ArtifactID: artifact.ID,
				Version:    v.Version,
			})
			if err != nil {
				return fmt.Errorf("inserting version %s: %w", v.Version, err)
			}
		}
		return nil
	})
	if err != nil {
		return nil, fmt.Errorf("storing new versions: %w", err)
	}

	return &StoreNewVersionsOutput{NewVersions: newVersions}, nil
}
