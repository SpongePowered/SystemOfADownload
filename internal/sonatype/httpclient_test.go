package sonatype

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
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
