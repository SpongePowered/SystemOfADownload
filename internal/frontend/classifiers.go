package frontend

import "strings"

// AssetLink represents a download button for a build.
type AssetLink struct {
	Name    string
	URL     string
	Primary bool
}

// isLegacyMC returns true if the MC version uses legacy classifiers.
// A version is legacy if it exactly matches a configured prefix (e.g. "1.8"
// from a ?minecraft=1.8 URL filter) or is a sub-version of one (e.g. "1.8.9"
// matches prefix "1.8"). The exact-match branch is load-bearing: without it,
// bare URL filters like "1.8" silently fall into the modern-era code path
// and every legacy build renders with zero download buttons.
func isLegacyMC(mcVersion string, prefixes []string) bool {
	for _, prefix := range prefixes {
		if mcVersion == prefix || strings.HasPrefix(mcVersion, prefix+".") {
			return true
		}
	}
	return false
}

// matchAssets matches API asset data against the platform's artifact type config,
// returning download links in the correct order (primary first).
// Only .jar assets are considered.
func matchAssets(
	assets []assetData,
	artifactTypes []ArtifactType,
	mcVersion string,
	legacyPrefixes []string,
) []AssetLink {
	legacy := isLegacyMC(mcVersion, legacyPrefixes)

	// Build a map from classifier to download URL for .jar assets.
	classifierToURL := make(map[string]string, len(assets))
	for _, a := range assets {
		if a.Extension == "jar" {
			classifierToURL[a.Classifier] = a.DownloadURL
		}
	}

	var primary []AssetLink
	var secondary []AssetLink

	for _, at := range artifactTypes {
		classifier := at.ClassifierModern
		if legacy {
			classifier = at.ClassifierLegacy
		}

		// Skip artifact types not available for this build era.
		// A type with empty modern classifier is legacy-only, and vice versa.
		if !legacy && at.ClassifierModern == "" {
			continue
		}

		url, ok := classifierToURL[classifier]
		if !ok {
			continue
		}

		link := AssetLink{
			Name:    at.Name,
			URL:     url,
			Primary: at.Primary,
		}
		if at.Primary {
			primary = append(primary, link)
		} else {
			secondary = append(secondary, link)
		}
	}

	return append(primary, secondary...)
}

// assetData is the intermediate representation of an asset from the service layer.
type assetData struct {
	Classifier  string
	DownloadURL string
	Extension   string
}
