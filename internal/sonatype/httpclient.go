package sonatype

import (
	"context"
	"encoding/json"
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

// ProgressFunc is called during paginated fetches with the versions so far
// and the next continuation token (empty if this was the last page).
type ProgressFunc func(versions []domain.VersionInfo, nextToken string)

// HTTPClient implements Client using the Sonatype REST API.
type HTTPClient struct {
	httpClient *http.Client
	baseURL    string
	repoName   string
	onProgress ProgressFunc // called during paginated fetches (optional)
}

// NewHTTPClient creates a new Sonatype Nexus HTTP client.
func NewHTTPClient(baseURL, repoName string) *HTTPClient {
	return &HTTPClient{
		httpClient: http.DefaultClient,
		baseURL:    strings.TrimRight(baseURL, "/"),
		repoName:   repoName,
	}
}

// WithProgress returns a copy of the client that calls fn after each page
// of paginated fetches. Use this to wire heartbeating into activities.
func (c *HTTPClient) WithProgress(fn ProgressFunc) *HTTPClient {
	clone := *c
	clone.onProgress = fn
	return &clone
}

// searchComponentsResponse models the Sonatype REST API v3 search response.
type searchComponentsResponse struct {
	Items             []searchComponentItem `json:"items"`
	ContinuationToken *string               `json:"continuationToken"`
}

type searchComponentItem struct {
	Version string `json:"version"`
}

// FetchVersions fetches all versions of an artifact using the Sonatype REST API
// component search. Calls onProgress after each page with the versions fetched
// so far (for heartbeating).
func (c *HTTPClient) FetchVersions(ctx context.Context, groupID, artifactID string) ([]domain.VersionInfo, error) {
	var versions []domain.VersionInfo
	seen := make(map[string]bool)
	var continuationToken *string

	for {
		params := url.Values{
			"group":      {groupID},
			"name":       {artifactID},
			"repository": {c.repoName},
		}
		if continuationToken != nil {
			params.Set("continuationToken", *continuationToken)
		}

		searchURL := fmt.Sprintf("%s/service/rest/v1/search?%s", c.baseURL, params.Encode())

		req, err := http.NewRequestWithContext(ctx, http.MethodGet, searchURL, http.NoBody)
		if err != nil {
			return nil, fmt.Errorf("creating search request: %w", err)
		}

		resp, err := c.httpClient.Do(req)
		if err != nil {
			return nil, fmt.Errorf("searching components: %w", err)
		}

		if resp.StatusCode != http.StatusOK {
			_ = resp.Body.Close()
			return nil, fmt.Errorf("unexpected status %d from search API", resp.StatusCode)
		}

		var searchResp searchComponentsResponse
		err = json.NewDecoder(resp.Body).Decode(&searchResp)
		_ = resp.Body.Close()
		if err != nil {
			return nil, fmt.Errorf("decoding search response: %w", err)
		}

		for _, item := range searchResp.Items {
			if !seen[item.Version] {
				seen[item.Version] = true
				versions = append(versions, domain.VersionInfo{
					GroupID:    groupID,
					ArtifactID: artifactID,
					Version:    item.Version,
				})
			}
		}

		if searchResp.ContinuationToken == nil {
			if c.onProgress != nil {
				c.onProgress(versions, "")
			}
			break
		}

		nextToken := *searchResp.ContinuationToken
		if c.onProgress != nil {
			c.onProgress(versions, nextToken)
		}
		continuationToken = &nextToken
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

		req, err := http.NewRequestWithContext(ctx, http.MethodGet, searchURL, http.NoBody)
		if err != nil {
			return nil, fmt.Errorf("creating search request: %w", err)
		}

		resp, err := c.httpClient.Do(req)
		if err != nil {
			return nil, fmt.Errorf("searching assets: %w", err)
		}

		if resp.StatusCode != http.StatusOK {
			_ = resp.Body.Close()
			return nil, fmt.Errorf("unexpected status %d from search API", resp.StatusCode)
		}

		var searchResp searchAssetsResponse
		err = json.NewDecoder(resp.Body).Decode(&searchResp)
		_ = resp.Body.Close()
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
