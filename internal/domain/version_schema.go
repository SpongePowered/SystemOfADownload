package domain

import (
	"cmp"
	"fmt"
	"regexp"
	"strconv"
	"strings"
	"sync"
)

// VersionSchema defines how to parse version strings for a specific artifact.
// When provided, ParseVersionWithSchema tries each Variant in order; the first
// regex match wins.
type VersionSchema struct {
	// UseMojangManifest controls whether Mojang manifest ordering should be
	// fetched and applied for Minecraft version segments.
	UseMojangManifest bool `json:"use_mojang_manifest"`
	// Variants are tried in order; the first whose Pattern matches wins.
	Variants []VersionFormatVariant `json:"variants"`
}

// VersionFormatVariant represents one possible format a version string can take.
//
// The Pattern regex must use named capture groups that correspond to Segment
// rules. Two special capture group names are handled automatically:
//   - (?P<qualifier>...) — parsed as a VersionQualifier (RC, BETA → QualifierRC; PRE → QualifierPre)
//   - (?P<build>...)     — parsed as the qualifier build number (int)
//
// If no "qualifier" group captures, the version defaults to QualifierRelease.
type VersionFormatVariant struct {
	Name     string        `json:"name"`     // e.g. "current", "beta-era"
	Pattern  string        `json:"pattern"`  // regex with named capture groups
	Segments []SegmentRule `json:"segments"` // rules for each captured segment
}

// SegmentRule defines how to parse and compare a named capture group.
type SegmentRule struct {
	// Name matches a named capture group in the variant's Pattern.
	Name string `json:"name"`
	// ParseAs controls how the captured string is parsed for comparison:
	//   "minecraft" — recursively parsed as a Minecraft version
	//   "dotted"    — parsed as dot-separated integers (e.g. "17.0.1")
	//   "integer"   — parsed as a single integer
	//   "ignore"    — captured but not used in comparison
	ParseAs string `json:"parse_as"`
	// TagKey, if non-empty, causes this segment to produce a version tag
	// with this key and the raw captured value.
	TagKey string `json:"tag_key,omitempty"`
}

// Valid ParseAs values for SegmentRule.
const (
	ParseAsMinecraft = "minecraft"
	ParseAsDotted    = "dotted"
	ParseAsInteger   = "integer"
	ParseAsIgnore    = "ignore"
)

// Validate checks that all variant patterns compile and all segment rules
// have valid ParseAs values. Call this after unmarshaling a schema from the
// database to fail early on misconfigured schemas.
func (s *VersionSchema) Validate() error {
	for i, v := range s.Variants {
		if _, err := compilePattern(v.Pattern); err != nil {
			return fmt.Errorf("variant %d (%q): %w", i, v.Name, err)
		}
		for j, seg := range v.Segments {
			switch seg.ParseAs {
			case ParseAsMinecraft, ParseAsDotted, ParseAsInteger, ParseAsIgnore:
				// valid
			default:
				return fmt.Errorf("variant %d (%q), segment %d (%q): unknown parse_as %q", i, v.Name, j, seg.Name, seg.ParseAs)
			}
		}
	}
	return nil
}

// ParsedSegment holds the parsed value of a single captured segment.
type ParsedSegment struct {
	Rule     SegmentRule
	Raw      string
	Version  *ParsedVersion // when ParseAs == "minecraft"
	Ints     []int          // when ParseAs == "dotted"
	IntValue int            // when ParseAs == "integer"
}

// --- regex compilation cache ---

var regexCache sync.Map // pattern string → *regexp.Regexp

func compilePattern(pattern string) (*regexp.Regexp, error) {
	if cached, ok := regexCache.Load(pattern); ok {
		return cached.(*regexp.Regexp), nil
	}
	re, err := regexp.Compile(pattern)
	if err != nil {
		return nil, fmt.Errorf("compiling version pattern %q: %w", pattern, err)
	}
	regexCache.Store(pattern, re)
	return re, nil
}

// ParseVersionWithSchema parses a version string using the given schema.
// It tries each variant in order; the first match wins. If schema is nil or
// no variant matches, it falls back to simple semver-like parsing.
func ParseVersionWithSchema(raw string, schema *VersionSchema) ParsedVersion {
	if schema == nil {
		return parseSimpleVersion(raw)
	}

	for i, variant := range schema.Variants {
		re, err := compilePattern(variant.Pattern)
		if err != nil {
			continue
		}
		match := re.FindStringSubmatch(raw)
		if match == nil {
			continue
		}

		pv := ParsedVersion{
			Raw:          raw,
			IsComposite:  true,
			Qualifier:    QualifierRelease,
			VariantName:  variant.Name,
			VariantOrder: i,
		}

		// Build name→value map from capture groups.
		groups := make(map[string]string)
		for i, name := range re.SubexpNames() {
			if i > 0 && name != "" && i < len(match) && match[i] != "" {
				groups[name] = match[i]
			}
		}

		// Handle special qualifier/build groups.
		if q, ok := groups["qualifier"]; ok {
			switch strings.ToUpper(q) {
			case "RC", "BETA":
				pv.Qualifier = QualifierRC
			case "PRE":
				pv.Qualifier = QualifierPre
			case "DEV", "SNAPSHOT":
				pv.Qualifier = QualifierSnapshot
			}
		}
		if b, ok := groups["build"]; ok {
			pv.QualifierNum, _ = strconv.Atoi(b)
		}

		// Parse each segment rule.
		segments := make([]ParsedSegment, 0, len(variant.Segments))
		for _, rule := range variant.Segments {
			rawVal, ok := groups[rule.Name]
			if !ok {
				continue
			}
			seg := ParsedSegment{Rule: rule, Raw: rawVal}
			switch rule.ParseAs {
			case "minecraft":
				mc := parseSimpleVersion(rawVal)
				seg.Version = &mc
			case "dotted":
				seg.Ints = parseDottedInts(rawVal)
			case "integer":
				seg.IntValue, _ = strconv.Atoi(rawVal)
			}
			segments = append(segments, seg)
		}
		pv.Segments = segments

		// Set Minecraft pointer for ManifestOrder lookups.
		for i := range pv.Segments {
			if pv.Segments[i].Rule.ParseAs == "minecraft" {
				pv.Minecraft = pv.Segments[i].Version
				break
			}
		}

		return pv
	}

	// No variant matched — fall back to simple parsing.
	return parseSimpleVersion(raw)
}

// ExtractTags returns a map of tag key → raw segment value for all segments
// that have a non-empty TagKey. This is used to surface version-component
// tags (e.g. "minecraft" → "1.21.10", "forge" → "2847") for API queries.
func (pv ParsedVersion) ExtractTags() map[string]string {
	if len(pv.Segments) == 0 {
		return nil
	}
	tags := make(map[string]string, len(pv.Segments))
	for _, seg := range pv.Segments {
		if seg.Rule.TagKey != "" {
			tags[seg.Rule.TagKey] = seg.Raw
		}
	}
	if len(tags) == 0 {
		return nil
	}
	return tags
}

// compareSegments compares two parsed segments using their ParseAs rule.
func compareSegments(a, b ParsedSegment) int {
	switch a.Rule.ParseAs {
	case "minecraft":
		if a.Version != nil && b.Version != nil {
			return CompareVersions(*a.Version, *b.Version)
		}
		if a.Version != nil {
			return 1
		}
		if b.Version != nil {
			return -1
		}
	case "dotted":
		return compareIntSlices(a.Ints, b.Ints)
	case "integer":
		return cmp.Compare(a.IntValue, b.IntValue)
	}
	return 0
}
