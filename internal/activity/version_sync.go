package activity

import (
	"context"
	"fmt"

	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/sonatype"
)

// VersionSyncActivities holds the dependencies for version sync activities.
type VersionSyncActivities struct {
	SonatypeClient sonatype.Client
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

// FetchVersions calls the Sonatype Nexus API to retrieve version info for an artifact.
func (a *VersionSyncActivities) FetchVersions(ctx context.Context, input FetchVersionsInput) (*FetchVersionsOutput, error) {
	versions, err := a.SonatypeClient.FetchVersions(ctx, input.GroupID, input.ArtifactID)
	if err != nil {
		return nil, fmt.Errorf("fetching versions: %w", err)
	}
	return &FetchVersionsOutput{Versions: versions}, nil
}
