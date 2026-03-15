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

	// For composite versions (e.g., "1.21.10-17.0.1-RC2547", "25w41a-18.0.0-RC2382"):
	IsComposite bool
	Minecraft   *ParsedVersion // parsed minecraft sub-version
	APIVersion  []int          // parsed API version (e.g., [17, 0, 1])

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
func (pv ParsedVersion) MinecraftVersionString() string {
	if pv.IsComposite && pv.Minecraft != nil {
		return pv.Minecraft.Raw
	}
	return pv.Raw
}

var (
	weeklySnapshotRe  = regexp.MustCompile(`^(\d+)w(\d+)([a-z])$`)
	rcSuffixRe        = regexp.MustCompile(`(?i)-RC(\d+)$`)
	dottedVersionRe   = regexp.MustCompile(`^\d+\.\d+(\.\d+)*$`)
	stdQualifierRe    = regexp.MustCompile(`(?i)^([\d.]+)-(pre|rc)[-.]?(\d+)$`)
	snapshotDashNumRe = regexp.MustCompile(`(?i)^([\d.]+)-snapshot-(\d+)$`)
	pureVersionRe     = regexp.MustCompile(`^[\d.]+$`)
)

// ParseVersion parses a version string into a ParsedVersion for comparison.
//
// Composite detection works right-to-left: strip optional -RC{N} suffix,
// then find the rightmost dash-separated segment that looks like a dotted
// version number (X.Y or X.Y.Z). If found and it's not the first segment,
// the version is composite: everything before it is the Minecraft version
// (parsed recursively), and the dotted segment is the API version.
//
// Supported formats:
//   - Weekly snapshot: "25w45a"
//   - Composite RC: "1.21.10-17.0.1-RC2547", "25w41a-18.0.0-RC2382",
//     "26.1-snapshot-2-18.0.0-RC2531"
//   - Composite release: "1.21.10-17.0.0"
//   - Snapshot-N: "26.1-snapshot-6"
//   - Standard qualifier: "1.21.10-pre1", "1.17-rc1"
//   - Pure version: "1.21.10"
func ParseVersion(raw string) ParsedVersion {
	// First try composite detection (handles the most complex cases).
	if pv, ok := tryParseComposite(raw); ok {
		return pv
	}

	// Non-composite parsing.
	return parseSimpleVersion(raw)
}

// tryParseComposite attempts to parse a composite version string like
// "1.21.10-17.0.1-RC2547" or "25w41a-18.0.0-RC2382" or
// "26.1-snapshot-2-18.0.0-RC2531".
func tryParseComposite(raw string) (ParsedVersion, bool) {
	pv := ParsedVersion{Raw: raw, IsComposite: true, Qualifier: QualifierRelease}

	remaining := raw

	// Step 1: Strip -RC{N} suffix if present.
	if m := rcSuffixRe.FindStringSubmatchIndex(remaining); m != nil {
		rcNumStr := remaining[m[2]:m[3]]
		pv.Qualifier = QualifierRC
		pv.QualifierNum, _ = strconv.Atoi(rcNumStr)
		remaining = remaining[:m[0]]
	}

	// Step 2: Split by '-' and find the rightmost dotted-version segment.
	segments := strings.Split(remaining, "-")
	apiIdx := -1
	for i := len(segments) - 1; i >= 0; i-- {
		if dottedVersionRe.MatchString(segments[i]) {
			apiIdx = i
			break
		}
	}

	// Must have a dotted API version that isn't the first segment
	// (there must be something left for the Minecraft version).
	if apiIdx <= 0 {
		return ParsedVersion{}, false
	}

	pv.APIVersion = parseDottedInts(segments[apiIdx])
	mcRaw := strings.Join(segments[:apiIdx], "-")

	// Step 3: Parse the Minecraft sub-version recursively.
	mc := parseSimpleVersion(mcRaw)
	pv.Minecraft = &mc

	return pv, true
}

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
func CompareVersions(a, b ParsedVersion) int {
	// If both have manifest ordering, that takes full precedence.
	if a.ManifestOrder > 0 && b.ManifestOrder > 0 {
		return cmp.Compare(a.ManifestOrder, b.ManifestOrder)
	}
	if a.ManifestOrder > 0 {
		return 1
	}
	if b.ManifestOrder > 0 {
		return -1
	}

	// Both composite: compare minecraft, then API, then qualifier.
	if a.IsComposite && b.IsComposite {
		if c := CompareVersions(*a.Minecraft, *b.Minecraft); c != 0 {
			return c
		}
		if c := compareIntSlices(a.APIVersion, b.APIVersion); c != 0 {
			return c
		}
		if c := cmp.Compare(int(a.Qualifier), int(b.Qualifier)); c != 0 {
			return c
		}
		return cmp.Compare(a.QualifierNum, b.QualifierNum)
	}

	// One composite, one not: compare the minecraft sub-component
	// against the non-composite version.
	if a.IsComposite {
		if c := CompareVersions(*a.Minecraft, b); c != 0 {
			return c
		}
		return 1 // composite is "more specific", sorts after
	}
	if b.IsComposite {
		if c := CompareVersions(a, *b.Minecraft); c != 0 {
			return c
		}
		return -1
	}

	// Both non-composite below.

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
