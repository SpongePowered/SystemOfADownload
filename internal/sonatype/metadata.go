package sonatype

import (
	"context"
	"encoding/xml"
	"errors"
	"fmt"
	"net/http"
	"strings"

	"go.temporal.io/sdk/temporal"

	"github.com/spongepowered/systemofadownload/internal/domain"
)

// MavenMetadata models the subset of maven-metadata.xml we care about — the
// list of published versions. Nexus serves this file per (repo, groupPath,
// artifactID) and regenerates it on deploy.
type MavenMetadata struct {
	Versioning struct {
		Versions struct {
			Version []string `xml:"version"`
		} `xml:"versions"`
	} `xml:"versioning"`
}

// FetchVersionsFromMetadata downloads maven-metadata.xml for the given GAV and
// returns the list of versions it declares. 404 is treated as "no versions
// published yet" and returns an empty slice (artifacts can register before the
// first deploy). 401/403 are wrapped as non-retryable — auth misconfig, retry
// won't help. 5xx surface as retryable errors (default activity retry).
//
// The maven-metadata.xml on a group repo (e.g. maven-public) is a merged view
// across the underlying hosted repos. Callers that care about per-repo denial
// (SONATYPE_REPO_DENY) should combine this with VersionHasAssets before acting
// on the returned list.
func (c *HTTPClient) FetchVersionsFromMetadata(ctx context.Context, groupID, artifactID string) ([]domain.VersionInfo, error) {
	groupPath := strings.ReplaceAll(groupID, ".", "/")
	metadataURL := fmt.Sprintf("%s/repository/%s/%s/%s/maven-metadata.xml",
		c.baseURL, c.repoName, groupPath, artifactID)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, metadataURL, http.NoBody)
	if err != nil {
		return nil, fmt.Errorf("creating metadata request: %w", err)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("fetching maven-metadata.xml: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	switch {
	case resp.StatusCode == http.StatusNotFound:
		return nil, nil
	case resp.StatusCode == http.StatusUnauthorized || resp.StatusCode == http.StatusForbidden:
		return nil, temporal.NewNonRetryableApplicationError(
			fmt.Sprintf("maven-metadata.xml returned %d", resp.StatusCode),
			"SonatypeAuthError",
			nil,
		)
	case resp.StatusCode != http.StatusOK:
		return nil, fmt.Errorf("unexpected status %d from %s", resp.StatusCode, metadataURL)
	}

	var meta MavenMetadata
	if err := xml.NewDecoder(resp.Body).Decode(&meta); err != nil {
		return nil, fmt.Errorf("decoding maven-metadata.xml: %w", err)
	}

	versions := meta.Versioning.Versions.Version
	if len(versions) == 0 {
		return nil, nil
	}

	out := make([]domain.VersionInfo, 0, len(versions))
	for _, v := range versions {
		out = append(out, domain.VersionInfo{
			GroupID:    groupID,
			ArtifactID: artifactID,
			Version:    v,
		})
	}
	return out, nil
}

// VersionHasAssets reports whether Sonatype currently exposes at least one
// downloadable, non-checksum, non-denied asset for the given GAV. It is the
// asset-presence probe used to gate metadata-sourced version inserts: the
// metadata XML can list a version before Nexus has finished indexing its
// assets (or after, but with only denied-repo copies available), producing a
// database row that would never receive assets.
//
// Short-circuits after the first accepted asset.
func (c *HTTPClient) VersionHasAssets(ctx context.Context, groupID, artifactID, version string) (bool, error) {
	assets, err := c.SearchAssets(ctx, groupID, artifactID, version)
	if err != nil {
		// Surface Temporal-friendly errors unchanged (non-retryable stays non-retryable).
		var appErr *temporal.ApplicationError
		if errors.As(err, &appErr) {
			return false, err
		}
		return false, fmt.Errorf("probing assets for %s:%s:%s: %w", groupID, artifactID, version, err)
	}
	return len(assets) > 0, nil
}
