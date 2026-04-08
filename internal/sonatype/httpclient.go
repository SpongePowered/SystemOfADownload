package sonatype

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strings"

	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"

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
		Md5    string `json:"md5"`
		Sha1   string `json:"sha1"`
		Sha256 string `json:"sha256"`
		Sha512 string `json:"sha512"`
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
	denyRepos  map[string]struct{} // hosted repo names whose assets should be dropped
	onProgress ProgressFunc        // called during paginated fetches (optional)
}

// NewHTTPClient creates a new Sonatype Nexus HTTP client.
//
// denyRepos is an optional set of hosted-repository names whose assets must be
// filtered out of SearchAssets results. This exists because maven-public is a
// group repo that can aggregate multiple hosted repos, and some legacy hosted
// repos (e.g. forge-proxy) return 403 on asset downloads while still being
// indexed by component/asset search. Without filtering, duplicate assets for
// the same GAV+classifier leak through and downstream activities may pick the
// unreachable copy. denyRepos applies only to SearchAssets; FetchVersions is
// unaffected so versions only present in a denied repo are still discovered.
func NewHTTPClient(baseURL, repoName string, denyRepos ...string) *HTTPClient {
	deny := make(map[string]struct{}, len(denyRepos))
	for _, r := range denyRepos {
		r = strings.TrimSpace(r)
		if r != "" {
			deny[r] = struct{}{}
		}
	}
	return &HTTPClient{
		httpClient: &http.Client{
			Transport: otelhttp.NewTransport(http.DefaultTransport),
		},
		baseURL:   strings.TrimRight(baseURL, "/"),
		repoName:  repoName,
		denyRepos: deny,
	}
}

// repoNameFromDownloadURL extracts the hosted repository name from a Nexus
// asset download URL of the form "<base>/repository/<repo>/<path>". Returns
// empty string if the URL doesn't match that shape.
func repoNameFromDownloadURL(downloadURL string) string {
	const marker = "/repository/"
	i := strings.Index(downloadURL, marker)
	if i < 0 {
		return ""
	}
	rest := downloadURL[i+len(marker):]
	j := strings.IndexByte(rest, '/')
	if j < 0 {
		return rest
	}
	return rest[:j]
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
			// Skip checksum files — their extension contains a dot (e.g. "jar.md5", "pom.sha1")
			if item.Maven2 != nil && strings.Contains(item.Maven2.Extension, ".") {
				continue
			}
			// Skip assets served from denied hosted repos. maven-public aggregates
			// several hosted repos and some legacy ones (e.g. forge-proxy) return
			// 403 on direct download even though they're indexed.
			if len(c.denyRepos) > 0 {
				if hosted := repoNameFromDownloadURL(item.DownloadURL); hosted != "" {
					if _, denied := c.denyRepos[hosted]; denied {
						continue
					}
				}
			}
			asset := domain.AssetInfo{
				DownloadURL: item.DownloadURL,
				Path:        item.Path,
				ContentType: item.ContentType,
				Md5:         item.Checksum.Md5,
				Sha1:        item.Checksum.Sha1,
				Sha256:      item.Checksum.Sha256,
				Sha512:      item.Checksum.Sha512,
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
