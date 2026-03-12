package sonatype

import (
	"context"

	"github.com/spongepowered/systemofadownload/internal/domain"
)

// Client defines the interface for fetching artifact version data from a Sonatype Nexus repository.
type Client interface {
	FetchVersions(ctx context.Context, groupID, artifactID string) ([]domain.VersionInfo, error)
}
