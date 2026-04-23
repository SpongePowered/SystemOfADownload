package sonatype

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"go.temporal.io/sdk/temporal"
)

func TestRepoNameFromDownloadURL(t *testing.T) {
	cases := map[string]string{
		"https://repo.spongepowered.org/repository/forge-proxy/org/spongepowered/spongeforge/1.12.2/spongeforge-1.12.2.jar": "forge-proxy",
		"https://repo.spongepowered.org/repository/legacy-transfer/foo/bar-1.0.jar":                                         "legacy-transfer",
		"https://repo.spongepowered.org/repository/maven-public/foo":                                                        "maven-public",
		"https://example.com/no-marker-here/foo.jar":                                                                        "",
		"": "",
	}
	for in, want := range cases {
		if got := repoNameFromDownloadURL(in); got != want {
			t.Errorf("repoNameFromDownloadURL(%q) = %q, want %q", in, got, want)
		}
	}
}

// TestSearchAssetsDeniesRepos verifies that assets served from denied hosted
// repos are dropped while assets from allowed repos pass through. This covers
// the forge-proxy / legacy-transfer duplicate situation observed in staging.
func TestSearchAssetsDeniesRepos(t *testing.T) {
	resp := searchAssetsResponse{
		Items: []searchAssetItem{
			{
				DownloadURL: "https://nexus.example/repository/forge-proxy/org/spongepowered/spongeforge/1.12.2-2768-7.1.6-RC3657/spongeforge-1.12.2-2768-7.1.6-RC3657.jar",
				Path:        "org/spongepowered/spongeforge/1.12.2-2768-7.1.6-RC3657/spongeforge-1.12.2-2768-7.1.6-RC3657.jar",
				Maven2:      &searchAssetMaven2{Extension: "jar"},
			},
			{
				DownloadURL: "https://nexus.example/repository/legacy-transfer/org/spongepowered/spongeforge/1.12.2-2768-7.1.6-RC3657/spongeforge-1.12.2-2768-7.1.6-RC3657.jar",
				Path:        "org/spongepowered/spongeforge/1.12.2-2768-7.1.6-RC3657/spongeforge-1.12.2-2768-7.1.6-RC3657.jar",
				Maven2:      &searchAssetMaven2{Extension: "jar"},
			},
			{
				// Checksum file — always dropped regardless of repo.
				DownloadURL: "https://nexus.example/repository/legacy-transfer/org/spongepowered/spongeforge/1.12.2-2768-7.1.6-RC3657/spongeforge-1.12.2-2768-7.1.6-RC3657.jar.sha1",
				Path:        "org/spongepowered/spongeforge/1.12.2-2768-7.1.6-RC3657/spongeforge-1.12.2-2768-7.1.6-RC3657.jar.sha1",
				Maven2:      &searchAssetMaven2{Extension: "jar.sha1"},
			},
		},
	}

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(resp)
	}))
	defer srv.Close()

	client := NewHTTPClient(srv.URL, "maven-public", "forge-proxy")

	assets, err := client.SearchAssets(context.Background(), "org.spongepowered", "spongeforge", "1.12.2-2768-7.1.6-RC3657")
	if err != nil {
		t.Fatalf("SearchAssets: %v", err)
	}

	if len(assets) != 1 {
		t.Fatalf("expected 1 asset after filtering forge-proxy and checksums, got %d: %+v", len(assets), assets)
	}
	if want := "legacy-transfer"; repoNameFromDownloadURL(assets[0].DownloadURL) != want {
		t.Errorf("surviving asset should be from %q, got URL %q", want, assets[0].DownloadURL)
	}
}

// TestSearchAssetsNoDenyList verifies that without a deny list all non-checksum
// assets are returned — ensuring the filter is opt-in and doesn't regress the
// default behavior.
func TestSearchAssetsNoDenyList(t *testing.T) {
	resp := searchAssetsResponse{
		Items: []searchAssetItem{
			{
				DownloadURL: "https://nexus.example/repository/forge-proxy/x/x/1.0/x-1.0.jar",
				Maven2:      &searchAssetMaven2{Extension: "jar"},
			},
			{
				DownloadURL: "https://nexus.example/repository/legacy-transfer/x/x/1.0/x-1.0.jar",
				Maven2:      &searchAssetMaven2{Extension: "jar"},
			},
		},
	}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_ = json.NewEncoder(w).Encode(resp)
	}))
	defer srv.Close()

	client := NewHTTPClient(srv.URL, "maven-public")
	assets, err := client.SearchAssets(context.Background(), "g", "a", "v")
	if err != nil {
		t.Fatalf("SearchAssets: %v", err)
	}
	if len(assets) != 2 {
		t.Fatalf("expected 2 assets without deny list, got %d", len(assets))
	}
}

// TestFetchVersionsFromMetadataHappyPath covers the standard case: Nexus
// returns a maven-metadata.xml listing several versions.
func TestFetchVersionsFromMetadataHappyPath(t *testing.T) {
	const body = `<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>org.spongepowered</groupId>
  <artifactId>spongevanilla</artifactId>
  <versioning>
    <versions>
      <version>1.21.1-12.0.0-RC45</version>
      <version>1.20.6-11.0.0</version>
      <version>1.20.4-10.0.0</version>
    </versions>
  </versioning>
</metadata>`
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		want := "/repository/maven-public/org/spongepowered/spongevanilla/maven-metadata.xml"
		if !strings.HasSuffix(r.URL.Path, want) {
			t.Errorf("unexpected path %q, want suffix %q", r.URL.Path, want)
		}
		_, _ = w.Write([]byte(body))
	}))
	defer srv.Close()

	client := NewHTTPClient(srv.URL, "maven-public")
	versions, err := client.FetchVersionsFromMetadata(context.Background(), "org.spongepowered", "spongevanilla")
	if err != nil {
		t.Fatalf("FetchVersionsFromMetadata: %v", err)
	}
	if got, want := len(versions), 3; got != want {
		t.Fatalf("got %d versions, want %d: %+v", got, want, versions)
	}
	if versions[0].Version != "1.21.1-12.0.0-RC45" {
		t.Errorf("first version = %q, want 1.21.1-12.0.0-RC45", versions[0].Version)
	}
}

// TestFetchVersionsFromMetadataNotFound verifies 404 yields an empty slice
// (no versions published yet) rather than an error.
func TestFetchVersionsFromMetadataNotFound(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusNotFound)
	}))
	defer srv.Close()

	client := NewHTTPClient(srv.URL, "maven-public")
	versions, err := client.FetchVersionsFromMetadata(context.Background(), "g", "a")
	if err != nil {
		t.Fatalf("expected no error on 404, got %v", err)
	}
	if len(versions) != 0 {
		t.Fatalf("expected empty slice on 404, got %d versions", len(versions))
	}
}

// TestFetchVersionsFromMetadataForbidden verifies 403 returns a non-retryable
// ApplicationError — retrying an auth failure will not help.
func TestFetchVersionsFromMetadataForbidden(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusForbidden)
	}))
	defer srv.Close()

	client := NewHTTPClient(srv.URL, "maven-public")
	_, err := client.FetchVersionsFromMetadata(context.Background(), "g", "a")
	if err == nil {
		t.Fatal("expected error on 403")
	}
	var appErr *temporal.ApplicationError
	if !errors.As(err, &appErr) || !appErr.NonRetryable() {
		t.Fatalf("expected non-retryable ApplicationError, got %T %v", err, err)
	}
}

// TestFetchVersionsFromMetadataServerError verifies 500 bubbles up as a
// retryable error (plain error, default activity retry applies).
func TestFetchVersionsFromMetadataServerError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()

	client := NewHTTPClient(srv.URL, "maven-public")
	_, err := client.FetchVersionsFromMetadata(context.Background(), "g", "a")
	if err == nil {
		t.Fatal("expected error on 500")
	}
	var appErr *temporal.ApplicationError
	if errors.As(err, &appErr) && appErr.NonRetryable() {
		t.Fatalf("5xx should be retryable, got non-retryable: %v", err)
	}
}

// TestFetchVersionsFromMetadataMalformedXML verifies we surface a parse error
// rather than silently returning empty.
func TestFetchVersionsFromMetadataMalformedXML(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte("<metadata><versioning><versions><version>1"))
	}))
	defer srv.Close()

	client := NewHTTPClient(srv.URL, "maven-public")
	_, err := client.FetchVersionsFromMetadata(context.Background(), "g", "a")
	if err == nil {
		t.Fatal("expected decode error on malformed XML")
	}
}

// TestFetchVersionsFromMetadataEmptyVersions covers a maven-metadata.xml that
// parses successfully but contains no <version> entries. Must return empty
// without error so downstream code treats it as "no versions yet".
func TestFetchVersionsFromMetadataEmptyVersions(t *testing.T) {
	const body = `<?xml version="1.0"?><metadata><versioning><versions></versions></versioning></metadata>`
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte(body))
	}))
	defer srv.Close()

	client := NewHTTPClient(srv.URL, "maven-public")
	versions, err := client.FetchVersionsFromMetadata(context.Background(), "g", "a")
	if err != nil {
		t.Fatalf("unexpected error on empty versions: %v", err)
	}
	if len(versions) != 0 {
		t.Fatalf("expected empty slice, got %d", len(versions))
	}
}

// TestVersionHasAssetsTrue covers the happy path: at least one non-checksum
// asset from an allowed repo.
func TestVersionHasAssetsTrue(t *testing.T) {
	resp := searchAssetsResponse{
		Items: []searchAssetItem{
			{
				DownloadURL: "https://nexus.example/repository/maven-releases/g/a/1.0/a-1.0.jar",
				Maven2:      &searchAssetMaven2{Extension: "jar"},
			},
		},
	}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_ = json.NewEncoder(w).Encode(resp)
	}))
	defer srv.Close()

	client := NewHTTPClient(srv.URL, "maven-public")
	ok, err := client.VersionHasAssets(context.Background(), "g", "a", "1.0")
	if err != nil {
		t.Fatalf("VersionHasAssets: %v", err)
	}
	if !ok {
		t.Error("expected true, got false")
	}
}

// TestVersionHasAssetsFalseAfterDenyFilter verifies that when maven-public
// aggregates a version whose only assets live in a denied hosted repo, the
// probe correctly reports no assets. This is the metadata-path protection
// against ghost-version inserts.
func TestVersionHasAssetsFalseAfterDenyFilter(t *testing.T) {
	resp := searchAssetsResponse{
		Items: []searchAssetItem{
			{
				DownloadURL: "https://nexus.example/repository/forge-proxy/g/a/1.0/a-1.0.jar",
				Maven2:      &searchAssetMaven2{Extension: "jar"},
			},
		},
	}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_ = json.NewEncoder(w).Encode(resp)
	}))
	defer srv.Close()

	client := NewHTTPClient(srv.URL, "maven-public", "forge-proxy")
	ok, err := client.VersionHasAssets(context.Background(), "g", "a", "1.0")
	if err != nil {
		t.Fatalf("VersionHasAssets: %v", err)
	}
	if ok {
		t.Error("expected false (only denied asset), got true")
	}
}

// TestVersionHasAssetsEmpty verifies an empty asset response returns false.
// This covers the "maven-metadata lists a version before Nexus indexes assets"
// race that motivates the probe.
func TestVersionHasAssetsEmpty(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_ = json.NewEncoder(w).Encode(searchAssetsResponse{})
	}))
	defer srv.Close()

	client := NewHTTPClient(srv.URL, "maven-public")
	ok, err := client.VersionHasAssets(context.Background(), "g", "a", "1.0")
	if err != nil {
		t.Fatalf("VersionHasAssets: %v", err)
	}
	if ok {
		t.Error("expected false on empty assets, got true")
	}
}
