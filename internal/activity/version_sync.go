package activity

import (
	"context"
	"fmt"

	"go.temporal.io/sdk/activity"

	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/repository"
	"github.com/spongepowered/systemofadownload/internal/sonatype"
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
}

// FetchVersionsOutput is the output of the FetchVersions activity.
type FetchVersionsOutput struct {
	Versions []domain.VersionInfo
}

// FetchVersions calls the Sonatype REST API to retrieve all version info for an artifact.
// It heartbeats a progress counter on each page to prove liveness. On retry, pagination
// restarts from scratch — the Sonatype API is idempotent and fast (~30s for all pages).
func (a *VersionSyncActivities) FetchVersions(ctx context.Context, input FetchVersionsInput) (*FetchVersionsOutput, error) {
	// Heartbeat just the count on each page (~8 bytes vs ~300KB for the full list)
	fetchClient := a.SonatypeClient
	if sc, ok := a.SonatypeClient.(*sonatype.HTTPClient); ok {
		fetchClient = sc.WithProgress(func(versions []domain.VersionInfo, _ string) {
			activity.RecordHeartbeat(ctx, len(versions))
		})
	}

	versions, err := fetchClient.FetchVersions(ctx, input.GroupID, input.ArtifactID)
	if err != nil {
		return nil, fmt.Errorf("fetching versions: %w", err)
	}

	return &FetchVersionsOutput{Versions: versions}, nil
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

	// Store new versions within a transaction.
	err = a.Repo.WithTx(ctx, func(tx repository.Tx) error {
		for _, v := range newVersions {
			_, err := tx.CreateArtifactVersion(ctx, db.CreateArtifactVersionParams{
				ArtifactID: artifact.ID,
				Version:    v.Version,
			})
			if err != nil {
				return fmt.Errorf("creating version %s: %w", v.Version, err)
			}
		}
		return nil
	})
	if err != nil {
		return nil, fmt.Errorf("storing new versions: %w", err)
	}

	return &StoreNewVersionsOutput{NewVersions: newVersions}, nil
}
