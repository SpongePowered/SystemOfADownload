package frontend

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestIsLegacyMC(t *testing.T) {
	tests := []struct {
		name      string
		mcVersion string
		want      bool
	}{
		// Bare URL filters — these are what ?minecraft=1.8 sends to the
		// handler, and the reason every 1.8-1.12 build rendered zero
		// download buttons on the staging frontend before this fix.
		{"bare 1.8", "1.8", true},
		{"bare 1.9", "1.9", true},
		{"bare 1.10", "1.10", true},
		{"bare 1.11", "1.11", true},
		{"bare 1.12", "1.12", true},
		// Full sub-versions inside legacy era.
		{"sub 1.8.9", "1.8.9", true},
		{"sub 1.10.2", "1.10.2", true},
		{"sub 1.12.2", "1.12.2", true},
		// Modern era must not match.
		{"bare 1.13", "1.13", false},
		{"sub 1.13.2", "1.13.2", false},
		{"bare 1.21", "1.21", false},
		{"sub 1.21.4", "1.21.4", false},
		// Pathological inputs that used to (or might) match by accident.
		// "1.1" must NOT match "1.10" or "1.11" or "1.12".
		{"bare 1.1 does not match 1.10", "1.1", false},
		// Empty and unrelated.
		{"empty", "", false},
		{"random", "banana", false},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			got := isLegacyMC(tc.mcVersion, legacyMCPrefixes)
			assert.Equal(t, tc.want, got)
		})
	}
}

func TestMatchAssets_LegacyBareURLFilter(t *testing.T) {
	// Reproduction of the staging bug: SpongeForge 1.8.9-1694-3.1.0-BETA-1070
	// has an empty-classifier main jar and no "universal" classifier. A URL
	// filter of ?minecraft=1.8 must render the main jar as the primary
	// Download button.
	assets := []assetData{
		{Classifier: "dev", DownloadURL: "https://example.invalid/sf-1.8.9-dev.jar", Extension: "jar"},
		{Classifier: "sources", DownloadURL: "https://example.invalid/sf-1.8.9-sources.jar", Extension: "jar"},
		{Classifier: "", DownloadURL: "https://example.invalid/sf-1.8.9.jar", Extension: "jar"},
		{Classifier: "", DownloadURL: "https://example.invalid/sf-1.8.9.pom", Extension: "pom"},
	}

	links := matchAssets(assets, spongeArtifactTypes, "1.8", legacyMCPrefixes)

	// Expect at least the primary Download button to be present.
	var primary *AssetLink
	for i := range links {
		if links[i].Primary {
			primary = &links[i]
			break
		}
	}
	if assert.NotNil(t, primary, "legacy bare-URL filter must produce a primary Download link") {
		assert.Equal(t, "Download", primary.Name)
		assert.Equal(t, "https://example.invalid/sf-1.8.9.jar", primary.URL)
	}
}
