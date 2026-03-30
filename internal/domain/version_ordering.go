package domain

import (
	"cmp"
	"regexp"
	"strconv"
	"strings"
)

// VersionQualifier represents the type of version qualifier, ordered from
// earliest (snapshot) to latest (release) for comparison purposes.
type VersionQualifier int

const (
	QualifierSnapshot VersionQualifier = iota // snapshots come first
	QualifierPre                              // pre-releases
	QualifierRC                               // release candidates
	QualifierRelease                          // final release
)

// ParsedVersion is a parsed version string that can be compared for ordering.
type ParsedVersion struct {
	Raw string

	// For non-composite versions:
	// Primary version components (e.g., [1, 21, 10] for "1.21.10").
	Primary      []int
	Qualifier    VersionQualifier
	QualifierNum int
	// Weekly is non-nil for Minecraft weekly snapshots (e.g., "25w45a").
	Weekly *WeeklySnapshot

	// For composite versions parsed by ParseVersionWithSchema:
	IsComposite  bool            // true when a schema variant matched
	Minecraft    *ParsedVersion  // parsed minecraft sub-version
	VariantName  string          // which schema variant matched (empty if no schema)
	VariantOrder int             // index of the matched variant; lower = newer format
	Segments     []ParsedSegment // schema-parsed segments (nil if no schema)

	// ManifestOrder is the position from an external version manifest.
	// Higher values = newer. Zero means unset.
	ManifestOrder int
}

// WeeklySnapshot holds parsed components of a Minecraft weekly snapshot like "25w45a".
type WeeklySnapshot struct {
	Year  int
	Week  int
	Build rune
}

// MinecraftVersionString returns the string to use for Mojang manifest lookups.
// For composite versions, this is the minecraft sub-component.
// For non-composite versions, this is the full version string.
func (pv ParsedVersion) MinecraftVersionString() string { //nolint:gocritic // value receiver used in sort comparisons
	if pv.IsComposite && pv.Minecraft != nil {
		return pv.Minecraft.Raw
	}
	return pv.Raw
}

var (
	weeklySnapshotRe  = regexp.MustCompile(`^(\d+)w(\d+)([a-z])$`)
	stdQualifierRe    = regexp.MustCompile(`(?i)^([\d.]+)-(pre|rc)[-.]?(\d+)$`)
	snapshotDashNumRe = regexp.MustCompile(`(?i)^([\d.]+)-snapshot-(\d+)$`)
	pureVersionRe     = regexp.MustCompile(`^[\d.]+$`)
)

// parseSimpleVersion parses a non-composite version string.
func parseSimpleVersion(raw string) ParsedVersion {
	pv := ParsedVersion{Raw: raw, Qualifier: QualifierRelease}

	// Weekly snapshot: 25w45a
	if m := weeklySnapshotRe.FindStringSubmatch(raw); m != nil {
		year, _ := strconv.Atoi(m[1])
		week, _ := strconv.Atoi(m[2])
		pv.Weekly = &WeeklySnapshot{Year: year, Week: week, Build: rune(m[3][0])}
		return pv
	}

	// Snapshot-N: 26.1-snapshot-6
	if m := snapshotDashNumRe.FindStringSubmatch(raw); m != nil {
		pv.Primary = parseDottedInts(m[1])
		pv.Qualifier = QualifierSnapshot
		pv.QualifierNum, _ = strconv.Atoi(m[2])
		return pv
	}

	// Standard qualifier: 1.21.10-pre1, 1.17-rc1
	if m := stdQualifierRe.FindStringSubmatch(raw); m != nil {
		pv.Primary = parseDottedInts(m[1])
		switch strings.ToLower(m[2]) {
		case "pre":
			pv.Qualifier = QualifierPre
		case "rc":
			pv.Qualifier = QualifierRC
		}
		pv.QualifierNum, _ = strconv.Atoi(m[3])
		return pv
	}

	// Pure version: 1.21.10
	if pureVersionRe.MatchString(raw) {
		pv.Primary = parseDottedInts(raw)
		return pv
	}

	// Fallback: try to extract leading version number.
	if idx := strings.IndexByte(raw, '-'); idx > 0 {
		pv.Primary = parseDottedInts(raw[:idx])
		pv.Qualifier = QualifierSnapshot
	}
	return pv
}

// CompareVersions compares two parsed versions.
// Returns negative if a < b, positive if a > b, zero if equal.
// "Less than" means older / lower sort_order.
func CompareVersions(a, b ParsedVersion) int { //nolint:gocritic // value params required by slices.SortStableFunc signature
	// If both have manifest ordering and they differ, use manifest order.
	// If equal, fall through to segment/qualifier comparison.
	switch {
	case a.ManifestOrder > 0 && b.ManifestOrder > 0:
		if c := cmp.Compare(a.ManifestOrder, b.ManifestOrder); c != 0 {
			return c
		}
	case a.ManifestOrder > 0:
		return 1
	case b.ManifestOrder > 0:
		return -1
	}

	// Both have schema segments: compare segment-by-segment, then qualifier/build.
	if len(a.Segments) > 0 && len(b.Segments) > 0 {
		minLen := len(a.Segments)
		if len(b.Segments) < minLen {
			minLen = len(b.Segments)
		}
		for i := 0; i < minLen; i++ {
			sa, sb := a.Segments[i], b.Segments[i]
			if sa.Rule.ParseAs != sb.Rule.ParseAs {
				// Cross-variant comparison: segments have incompatible types.
				// Fall through to variant order tiebreaker below.
				break
			}
			if c := compareSegments(sa, sb); c != 0 {
				return c
			}
		}
		// Different variants within the same schema: lower index = newer format.
		if a.VariantOrder != b.VariantOrder {
			return -cmp.Compare(a.VariantOrder, b.VariantOrder)
		}
		if c := cmp.Compare(int(a.Qualifier), int(b.Qualifier)); c != 0 {
			return c
		}
		return cmp.Compare(a.QualifierNum, b.QualifierNum)
	}

	// Both non-composite below (simple versions or schema fallback).

	// Weekly snapshots without manifest: compare by year/week/build.
	if a.Weekly != nil && b.Weekly != nil {
		if c := cmp.Compare(a.Weekly.Year, b.Weekly.Year); c != 0 {
			return c
		}
		if c := cmp.Compare(a.Weekly.Week, b.Weekly.Week); c != 0 {
			return c
		}
		return cmp.Compare(int(a.Weekly.Build), int(b.Weekly.Build))
	}
	if a.Weekly != nil {
		return -1
	}
	if b.Weekly != nil {
		return 1
	}

	// Compare primary version components.
	if c := compareIntSlices(a.Primary, b.Primary); c != 0 {
		return c
	}

	// Compare qualifier rank: snapshot < pre < rc < release.
	if c := cmp.Compare(int(a.Qualifier), int(b.Qualifier)); c != 0 {
		return c
	}

	// Compare qualifier/build number.
	return cmp.Compare(a.QualifierNum, b.QualifierNum)
}

func parseDottedInts(s string) []int {
	parts := strings.Split(s, ".")
	result := make([]int, 0, len(parts))
	for _, p := range parts {
		n, err := strconv.Atoi(p)
		if err != nil {
			continue
		}
		result = append(result, n)
	}
	return result
}

func compareIntSlices(a, b []int) int {
	maxLen := len(a)
	if len(b) > maxLen {
		maxLen = len(b)
	}
	for i := 0; i < maxLen; i++ {
		va, vb := 0, 0
		if i < len(a) {
			va = a[i]
		}
		if i < len(b) {
			vb = b[i]
		}
		if c := cmp.Compare(va, vb); c != 0 {
			return c
		}
	}
	return 0
}
