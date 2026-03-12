package sonatype

import (
	"context"
	"encoding/xml"
	"fmt"
	"net/http"
	"strings"

	"github.com/spongepowered/systemofadownload/internal/domain"
)

// mavenMetadata models the maven-metadata.xml structure.
type mavenMetadata struct {
	GroupID    string `xml:"groupId"`
	ArtifactID string `xml:"artifactId"`
	Versioning struct {
		Versions struct {
			Version []string `xml:"version"`
		} `xml:"versions"`
	} `xml:"versioning"`
}

// HTTPClient implements Client by fetching maven-metadata.xml from a Nexus 3 repository.
type HTTPClient struct {
	httpClient *http.Client
	baseURL    string
	repoName   string
}

// NewHTTPClient creates a new Sonatype Nexus HTTP client.
func NewHTTPClient(baseURL, repoName string) *HTTPClient {
	return &HTTPClient{
		httpClient: http.DefaultClient,
		baseURL:    strings.TrimRight(baseURL, "/"),
		repoName:   repoName,
	}
}

// FetchVersions fetches all versions of an artifact from the Nexus maven-metadata.xml.
func (c *HTTPClient) FetchVersions(ctx context.Context, groupID, artifactID string) ([]domain.VersionInfo, error) {
	// Convert groupID dots to path separators (e.g. "org.spongepowered" -> "org/spongepowered")
	groupPath := strings.ReplaceAll(groupID, ".", "/")
	url := fmt.Sprintf("%s/repository/%s/%s/%s/maven-metadata.xml", c.baseURL, c.repoName, groupPath, artifactID)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, fmt.Errorf("creating request: %w", err)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("fetching maven-metadata.xml: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("unexpected status %d from %s", resp.StatusCode, url)
	}

	var metadata mavenMetadata
	if err := xml.NewDecoder(resp.Body).Decode(&metadata); err != nil {
		return nil, fmt.Errorf("decoding maven-metadata.xml: %w", err)
	}

	versions := make([]domain.VersionInfo, 0, len(metadata.Versioning.Versions.Version))
	for _, v := range metadata.Versioning.Versions.Version {
		versions = append(versions, domain.VersionInfo{
			GroupID:    groupID,
			ArtifactID: artifactID,
			Version:    v,
		})
	}
	return versions, nil
}
