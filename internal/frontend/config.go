package frontend

// PlatformConfig defines a downloadable platform shown on the frontend.
type PlatformConfig struct {
	ID               string
	Group            string
	Name             string
	Suffix           string
	Featured         bool
	Description      string
	BadgeColor       string
	ArtifactTypes    []ArtifactType
	QueryModifiers   map[string]map[string]string // mc_version -> {tag_key: tag_value}
	LegacyMCPrefixes []string                     // MC versions starting with these use legacy classifiers
}

// ArtifactType defines a downloadable artifact classifier mapping.
type ArtifactType struct {
	Name             string
	ClassifierModern string // empty string means not available for modern builds
	ClassifierLegacy string
	Primary          bool
	Extension        string
}

// spongeArtifactTypes is shared across all three platforms.
var spongeArtifactTypes = []ArtifactType{
	{Name: "Download", ClassifierModern: "universal", ClassifierLegacy: "", Primary: true, Extension: "jar"},
	{Name: "Sources", ClassifierModern: "sources-dev", ClassifierLegacy: "sources", Primary: false, Extension: "jar"},
	{Name: "Dev", ClassifierModern: "", ClassifierLegacy: "dev-shaded", Primary: false, Extension: "jar"},
}

// spongeQueryModifiers is shared across all three platforms.
var spongeQueryModifiers = map[string]map[string]string{
	"1.10":   {"api": "5"},
	"1.10.2": {"api": "5"},
	"1.11":   {"api": "6"},
	"1.11.2": {"api": "6"},
	"1.12":   {"api": "7"},
	"1.12.1": {"api": "7"},
	"1.12.2": {"api": "7"},
}

var legacyMCPrefixes = []string{"1.8.", "1.9.", "1.10.", "1.11.", "1.12."}

// defaultPlatforms returns the hardcoded platform list.
// This will be replaced by YAML config loading in a later phase.
func defaultPlatforms() []PlatformConfig {
	return []PlatformConfig{
		{
			ID:               "spongevanilla",
			Group:            "org.spongepowered",
			Name:             "SpongeVanilla",
			Suffix:           "Vanilla",
			Featured:         true,
			Description:      "Not using any mods? SpongeVanilla is for you!",
			BadgeColor:       "#917300",
			ArtifactTypes:    spongeArtifactTypes,
			QueryModifiers:   spongeQueryModifiers,
			LegacyMCPrefixes: legacyMCPrefixes,
		},
		{
			ID:               "spongeforge",
			Group:            "org.spongepowered",
			Name:             "SpongeForge",
			Suffix:           "Forge",
			Featured:         true,
			Description:      "Using Forge mods? Install the SpongeForge mod to use mods and plugins together!",
			BadgeColor:       "#910020",
			ArtifactTypes:    spongeArtifactTypes,
			QueryModifiers:   spongeQueryModifiers,
			LegacyMCPrefixes: legacyMCPrefixes,
		},
		{
			ID:               "spongeneo",
			Group:            "org.spongepowered",
			Name:             "SpongeNeo",
			Suffix:           "Neo",
			Featured:         true,
			Description:      "Using NeoForge mods? Install the SpongeNeo to use mods and plugins together!",
			BadgeColor:       "#cc6f2f",
			ArtifactTypes:    spongeArtifactTypes,
			QueryModifiers:   spongeQueryModifiers,
			LegacyMCPrefixes: legacyMCPrefixes,
		},
	}
}
