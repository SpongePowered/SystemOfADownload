package sonatype

import (
	"context"
	"encoding/json"
	"encoding/xml"
	"fmt"
	"net/http"
	"net/url"
	"strings"

	"github.com/spongepowered/systemofadownload/internal/domain"
)

// searchAssetsResponse models the Sonatype REST API v3 search/assets response.
type searchAssetsResponse struct {
	Items             []searchAssetItem `json:"items"`
	ContinuationToken *string           `json:"continuationToken"`
}

type searchAssetItem struct {
	DownloadURL string `json:"downloadUrl"`
	Path        string `json:"path"`
	ContentType string `json:"contentType"`
	Checksum    struct {
		Sha256 string `json:"sha256"`
	} `json:"checksum"`
	Maven2 *searchAssetMaven2 `json:"maven2"`
}

type searchAssetMaven2 struct {
	Extension  string `json:"extension"`
	Classifier string `json:"classifier"`
}

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

// SearchAssets uses the Sonatype REST API v3 to search for assets of a specific version.
func (c *HTTPClient) SearchAssets(ctx context.Context, groupID, artifactID, version string) ([]domain.AssetInfo, error) {
	var allAssets []domain.AssetInfo
	var continuationToken *string

	for {
		params := url.Values{
			"group":      {groupID},
			"name":       {artifactID},
			"version":    {version},
			"repository": {c.repoName},
		}
		if continuationToken != nil {
			params.Set("continuationToken", *continuationToken)
		}

		searchURL := fmt.Sprintf("%s/service/rest/v1/search/assets?%s", c.baseURL, params.Encode())

		req, err := http.NewRequestWithContext(ctx, http.MethodGet, searchURL, nil)
		if err != nil {
			return nil, fmt.Errorf("creating search request: %w", err)
		}

		resp, err := c.httpClient.Do(req)
		if err != nil {
			return nil, fmt.Errorf("searching assets: %w", err)
		}

		if resp.StatusCode != http.StatusOK {
			resp.Body.Close()
			return nil, fmt.Errorf("unexpected status %d from search API", resp.StatusCode)
		}

		var searchResp searchAssetsResponse
		err = json.NewDecoder(resp.Body).Decode(&searchResp)
		resp.Body.Close()
		if err != nil {
			return nil, fmt.Errorf("decoding search response: %w", err)
		}

		for _, item := range searchResp.Items {
			asset := domain.AssetInfo{
				DownloadURL: item.DownloadURL,
				Path:        item.Path,
				ContentType: item.ContentType,
				Sha256:      item.Checksum.Sha256,
			}
			if item.Maven2 != nil {
				asset.Extension = item.Maven2.Extension
				asset.Classifier = item.Maven2.Classifier
			}
			allAssets = append(allAssets, asset)
		}

		if searchResp.ContinuationToken == nil {
			break
		}
		continuationToken = searchResp.ContinuationToken
	}

	return allAssets, nil
}
