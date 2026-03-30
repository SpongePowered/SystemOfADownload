package domain

import (
	"slices"
	"testing"
)

var testMCPattern = `(?:\d+w\d+[a-z]|\d+\.\d+(?:\.\d+)?-snapshot-\d+|\d+\.\d+(?:\.\d+)?-(?:pre|rc)-?\d+|\d+\.\d+(?:\.\d+)?)`

var testSchema = &VersionSchema{
	UseMojangManifest: true,
	Variants: []VersionFormatVariant{{
		Name:    "current",
		Pattern: `^(?P<minecraft>` + testMCPattern + `)-(?P<api>\d+\.\d+\.\d+)(?:-(?P<qualifier>RC)(?P<build>\d+))?$`,
		Segments: []SegmentRule{
			{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
			{Name: "api", ParseAs: "dotted", TagKey: "api"},
		},
	}},
}

func TestRCBuildNumberOrdering(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name     string
		versions []string
		want     []string // expected ascending order (oldest → newest)
	}{
		{
			name: "26.1 release RCs sort by build number",
			versions: []string{
				"26.1-19.0.0-RC2578",
				"26.1-19.0.0-RC2577",
			},
			want: []string{
				"26.1-19.0.0-RC2577",
				"26.1-19.0.0-RC2578",
			},
		},
		{
			name: "snapshot-10 RCs sort by build number",
			versions: []string{
				"26.1-snapshot-10-19.0.0-RC2565",
				"26.1-snapshot-10-19.0.0-RC2566",
			},
			want: []string{
				"26.1-snapshot-10-19.0.0-RC2565",
				"26.1-snapshot-10-19.0.0-RC2566",
			},
		},
		{
			name: "snapshot-2 RCs sort by build number",
			versions: []string{
				"26.1-snapshot-2-18.0.0-RC2531",
				"26.1-snapshot-2-18.0.0-RC2530",
			},
			want: []string{
				"26.1-snapshot-2-18.0.0-RC2530",
				"26.1-snapshot-2-18.0.0-RC2531",
			},
		},
		{
			name: "snapshot-1 RCs sort by build number",
			versions: []string{
				"26.1-snapshot-1-18.0.0-RC2484",
				"26.1-snapshot-1-18.0.0-RC2479",
				"26.1-snapshot-1-18.0.0-RC2481",
				"26.1-snapshot-1-18.0.0-RC2480",
			},
			want: []string{
				"26.1-snapshot-1-18.0.0-RC2479",
				"26.1-snapshot-1-18.0.0-RC2480",
				"26.1-snapshot-1-18.0.0-RC2481",
				"26.1-snapshot-1-18.0.0-RC2484",
			},
		},
		{
			name: "full 26.1 set in correct descending order",
			versions: []string{
				"26.1-snapshot-1-18.0.0-RC2479",
				"26.1-snapshot-1-18.0.0-RC2480",
				"26.1-snapshot-1-18.0.0-RC2481",
				"26.1-snapshot-1-18.0.0-RC2484",
				"26.1-snapshot-2-18.0.0-RC2530",
				"26.1-snapshot-2-18.0.0-RC2531",
				"26.1-snapshot-10-19.0.0-RC2565",
				"26.1-snapshot-10-19.0.0-RC2566",
				"26.1-pre-2-19.0.0-RC2572",
				"26.1-pre-3-19.0.0-RC2573",
				"26.1-rc-1-19.0.0-RC2574",
				"26.1-rc-2-19.0.0-RC2575",
				"26.1-rc-3-19.0.0-RC2576",
				"26.1-19.0.0-RC2577",
				"26.1-19.0.0-RC2578",
			},
			want: []string{
				"26.1-snapshot-1-18.0.0-RC2479",
				"26.1-snapshot-1-18.0.0-RC2480",
				"26.1-snapshot-1-18.0.0-RC2481",
				"26.1-snapshot-1-18.0.0-RC2484",
				"26.1-snapshot-2-18.0.0-RC2530",
				"26.1-snapshot-2-18.0.0-RC2531",
				"26.1-snapshot-10-19.0.0-RC2565",
				"26.1-snapshot-10-19.0.0-RC2566",
				"26.1-pre-2-19.0.0-RC2572",
				"26.1-pre-3-19.0.0-RC2573",
				"26.1-rc-1-19.0.0-RC2574",
				"26.1-rc-2-19.0.0-RC2575",
				"26.1-rc-3-19.0.0-RC2576",
				"26.1-19.0.0-RC2577",
				"26.1-19.0.0-RC2578",
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// Parse all versions
			parsed := make([]ParsedVersion, len(tt.versions))
			for i, v := range tt.versions {
				parsed[i] = ParseVersionWithSchema(v, testSchema)
				t.Logf("parsed %-45s qualifier=%d qualifierNum=%d variant=%s",
					v, parsed[i].Qualifier, parsed[i].QualifierNum, parsed[i].VariantName)
			}

			// Sort ascending (same as ComputeVersionOrdering)
			slices.SortStableFunc(parsed, CompareVersions)

			// Verify order
			got := make([]string, len(parsed))
			for i, p := range parsed {
				got[i] = p.Raw
			}

			for i, v := range tt.want {
				if i >= len(got) {
					t.Errorf("missing version at index %d: want %s", i, v)
					continue
				}
				if got[i] != v {
					t.Errorf("index %d: want %s, got %s", i, v, got[i])
				}
			}

			if t.Failed() {
				t.Logf("got order:")
				for i, v := range got {
					t.Logf("  [%d] %s", i, v)
				}
			}
		})
	}
}

func TestRCBuildNumberOrderingWithManifest(t *testing.T) {
	t.Parallel()

	// Simulate manifest ordering: map MC version string → position (higher = newer).
	// This replicates what ComputeVersionOrdering does with the Mojang manifest.
	manifestOrder := map[string]int{
		"26.1":             11,
		"26.1-rc-3":        10,
		"26.1-rc-2":        9,
		"26.1-rc-1":        8,
		"26.1-pre-3":       7,
		"26.1-pre-2":       6,
		"26.1-snapshot-10": 5,
		"26.1-snapshot-2":  4,
		"26.1-snapshot-1":  3,
		"1.21.11":          2,
		"1.21.10":          1,
	}

	versions := []string{
		"26.1-snapshot-1-18.0.0-RC2479",
		"26.1-snapshot-1-18.0.0-RC2484",
		"26.1-snapshot-1-18.0.0-RC2481",
		"26.1-snapshot-1-18.0.0-RC2480",
		"26.1-snapshot-10-19.0.0-RC2566",
		"26.1-snapshot-10-19.0.0-RC2565",
		"26.1-19.0.0-RC2578",
		"26.1-19.0.0-RC2577",
	}

	want := []string{
		"26.1-snapshot-1-18.0.0-RC2479",
		"26.1-snapshot-1-18.0.0-RC2480",
		"26.1-snapshot-1-18.0.0-RC2481",
		"26.1-snapshot-1-18.0.0-RC2484",
		"26.1-snapshot-10-19.0.0-RC2565",
		"26.1-snapshot-10-19.0.0-RC2566",
		"26.1-19.0.0-RC2577",
		"26.1-19.0.0-RC2578",
	}

	parsed := make([]ParsedVersion, len(versions))
	for i, v := range versions {
		parsed[i] = ParseVersionWithSchema(v, testSchema)
		// Apply manifest ordering the same way the activity does
		mcStr := parsed[i].MinecraftVersionString()
		if pos, ok := manifestOrder[mcStr]; ok {
			parsed[i].ManifestOrder = pos
			if parsed[i].IsComposite && parsed[i].Minecraft != nil {
				parsed[i].Minecraft.ManifestOrder = pos
			}
		}
		t.Logf("%-45s mc=%s manifestOrder=%d qualifier=%d qualifierNum=%d",
			v, mcStr, parsed[i].ManifestOrder, parsed[i].Qualifier, parsed[i].QualifierNum)
	}

	slices.SortStableFunc(parsed, CompareVersions)

	got := make([]string, len(parsed))
	for i, p := range parsed {
		got[i] = p.Raw
	}

	for i, v := range want {
		if i >= len(got) || got[i] != v {
			t.Errorf("index %d: want %s, got %s", i, v, got[i])
		}
	}

	if t.Failed() {
		t.Logf("got order:")
		for i, p := range parsed {
			t.Logf("  [%d] %s (manifestOrder=%d)", i, p.Raw, p.ManifestOrder)
		}
	}
}

func TestRCBuildNumberPairwiseComparison(t *testing.T) {
	t.Parallel()

	pairs := []struct {
		lower  string
		higher string
	}{
		{"26.1-19.0.0-RC2577", "26.1-19.0.0-RC2578"},
		{"26.1-snapshot-10-19.0.0-RC2565", "26.1-snapshot-10-19.0.0-RC2566"},
		{"26.1-snapshot-2-18.0.0-RC2530", "26.1-snapshot-2-18.0.0-RC2531"},
		{"26.1-snapshot-1-18.0.0-RC2479", "26.1-snapshot-1-18.0.0-RC2480"},
		{"26.1-snapshot-1-18.0.0-RC2480", "26.1-snapshot-1-18.0.0-RC2481"},
		{"26.1-snapshot-1-18.0.0-RC2481", "26.1-snapshot-1-18.0.0-RC2484"},
		// Cross-MC-qualifier: release > rc > pre > snapshot
		{"26.1-pre-3-19.0.0-RC2573", "26.1-rc-1-19.0.0-RC2574"},
		{"26.1-rc-3-19.0.0-RC2576", "26.1-19.0.0-RC2577"},
		{"26.1-snapshot-10-19.0.0-RC2566", "26.1-pre-2-19.0.0-RC2572"},
	}

	for _, p := range pairs {
		t.Run(p.lower+" < "+p.higher, func(t *testing.T) {
			t.Parallel()
			a := ParseVersionWithSchema(p.lower, testSchema)
			b := ParseVersionWithSchema(p.higher, testSchema)

			c := CompareVersions(a, b)
			if c >= 0 {
				t.Errorf("CompareVersions(%s, %s) = %d, want negative",
					p.lower, p.higher, c)
				t.Logf("a: qualifier=%d qualifierNum=%d segments=%v", a.Qualifier, a.QualifierNum, a.Segments)
				t.Logf("b: qualifier=%d qualifierNum=%d segments=%v", b.Qualifier, b.QualifierNum, b.Segments)
			}
		})
	}
}
