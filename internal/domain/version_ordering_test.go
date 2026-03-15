package domain_test

import (
	"slices"
	"testing"

	"github.com/spongepowered/systemofadownload/internal/domain"
)

// mcPattern matches all known Minecraft version formats for use in composite
// version regexes. Order matters: more specific patterns first.
const mcPattern = `(?:\d+w\d+[a-z]|\d+\.\d+(?:\.\d+)?-snapshot-\d+|\d+\.\d+(?:\.\d+)?-(?:pre|rc)\d+|\d+\.\d+(?:\.\d+)?)`

func spongeVanillaSchema() *domain.VersionSchema {
	return &domain.VersionSchema{
		UseMojangManifest: true,
		Variants: []domain.VersionFormatVariant{
			{
				Name:    "current",
				Pattern: `^(?P<minecraft>` + mcPattern + `)-(?P<api>\d+\.\d+\.\d+)(?:-(?P<qualifier>RC)(?P<build>\d+))?$`,
				Segments: []domain.SegmentRule{
					{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
					{Name: "api", ParseAs: "dotted", TagKey: "api"},
				},
			},
			{
				Name:    "beta-era",
				Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<api>\d+\.\d+\.\d+)-(?P<qualifier>BETA)-(?P<build>\d+)$`,
				Segments: []domain.SegmentRule{
					{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
					{Name: "api", ParseAs: "dotted", TagKey: "api"},
				},
			},
		},
	}
}

func spongeForgeSchema() *domain.VersionSchema {
	return &domain.VersionSchema{
		UseMojangManifest: true,
		Variants: []domain.VersionFormatVariant{
			{
				Name:    "current",
				Pattern: `^(?P<minecraft>` + mcPattern + `)-(?P<forge>\d+(?:\.\d+)*)-(?P<api>\d+\.\d+\.\d+)(?:-(?P<qualifier>RC)(?P<build>\d+))?$`,
				Segments: []domain.SegmentRule{
					{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
					{Name: "forge", ParseAs: "dotted", TagKey: "forge"},
					{Name: "api", ParseAs: "dotted", TagKey: "api"},
				},
			},
			{
				Name:    "beta-era",
				Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<forge>\d+)-(?P<api>\d+\.\d+\.\d+)-(?P<qualifier>BETA)-(?P<build>\d+)$`,
				Segments: []domain.SegmentRule{
					{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
					{Name: "forge", ParseAs: "integer", TagKey: "forge"},
					{Name: "api", ParseAs: "dotted", TagKey: "api"},
				},
			},
		},
	}
}

func spongeNeoSchema() *domain.VersionSchema {
	return &domain.VersionSchema{
		UseMojangManifest: true,
		Variants: []domain.VersionFormatVariant{
			{
				Name:    "current",
				Pattern: `^(?P<minecraft>` + mcPattern + `)-(?P<neo>\d+\.\d+(?:\.\d+)?)(?:-beta)?-(?P<api>\d+\.\d+\.\d+)(?:-(?P<qualifier>RC)(?P<build>\d+))?$`,
				Segments: []domain.SegmentRule{
					{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
					{Name: "neo", ParseAs: "dotted", TagKey: "neoforge"},
					{Name: "api", ParseAs: "dotted", TagKey: "api"},
				},
			},
		},
	}
}

// --- Simple version parsing (nil schema fallback) ---

func TestParseSimpleVersion(t *testing.T) {
	t.Parallel()

	tests := []struct {
		raw       string
		primary   []int
		qualifier domain.VersionQualifier
		qualNum   int
		weekly    bool
	}{
		{raw: "1.8", primary: []int{1, 8}, qualifier: domain.QualifierRelease},
		{raw: "1.21", primary: []int{1, 21}, qualifier: domain.QualifierRelease},
		{raw: "1.21.10", primary: []int{1, 21, 10}, qualifier: domain.QualifierRelease},
		{raw: "1.17-pre1", primary: []int{1, 17}, qualifier: domain.QualifierPre, qualNum: 1},
		{raw: "1.17-pre2", primary: []int{1, 17}, qualifier: domain.QualifierPre, qualNum: 2},
		{raw: "1.21.11-pre1", primary: []int{1, 21, 11}, qualifier: domain.QualifierPre, qualNum: 1},
		{raw: "1.17-rc1", primary: []int{1, 17}, qualifier: domain.QualifierRC, qualNum: 1},
		{raw: "1.17.1-rc2", primary: []int{1, 17, 1}, qualifier: domain.QualifierRC, qualNum: 2},
		{raw: "25w45a", weekly: true},
		{raw: "26.1-snapshot-6", primary: []int{26, 1}, qualifier: domain.QualifierSnapshot, qualNum: 6},
	}

	for _, tt := range tests {
		t.Run(tt.raw, func(t *testing.T) {
			t.Parallel()
			pv := domain.ParseVersionWithSchema(tt.raw, nil)

			if pv.IsComposite {
				t.Fatalf("expected non-composite, got composite")
			}
			if tt.weekly {
				if pv.Weekly == nil {
					t.Fatal("expected weekly snapshot, got nil")
				}
				return
			}
			if !slices.Equal(pv.Primary, tt.primary) {
				t.Errorf("Primary: want %v, got %v", tt.primary, pv.Primary)
			}
			if pv.Qualifier != tt.qualifier {
				t.Errorf("Qualifier: want %d, got %d", tt.qualifier, pv.Qualifier)
			}
			if pv.QualifierNum != tt.qualNum {
				t.Errorf("QualifierNum: want %d, got %d", tt.qualNum, pv.QualifierNum)
			}
		})
	}
}

func TestMinecraftVersionString_Simple(t *testing.T) {
	t.Parallel()

	tests := []struct {
		raw  string
		want string
	}{
		{"1.21.10", "1.21.10"},
		{"1.21.10-pre1", "1.21.10-pre1"},
		{"25w45a", "25w45a"},
	}

	for _, tt := range tests {
		t.Run(tt.raw, func(t *testing.T) {
			t.Parallel()
			pv := domain.ParseVersionWithSchema(tt.raw, nil)
			got := pv.MinecraftVersionString()
			if got != tt.want {
				t.Errorf("MinecraftVersionString: want %q, got %q", tt.want, got)
			}
		})
	}
}

// --- Simple version comparison ---

func assertSimpleOrder(t *testing.T, older, newer string) {
	t.Helper()
	a := domain.ParseVersionWithSchema(older, nil)
	b := domain.ParseVersionWithSchema(newer, nil)
	if domain.CompareVersions(a, b) >= 0 {
		t.Errorf("expected %q < %q, but CompareVersions returned >= 0", older, newer)
	}
	if domain.CompareVersions(b, a) <= 0 {
		t.Errorf("expected %q > %q, but CompareVersions returned <= 0", newer, older)
	}
}

func TestCompareVersions_MinecraftOrdering(t *testing.T) {
	t.Parallel()

	t.Run("basic version ordering", func(t *testing.T) {
		assertSimpleOrder(t, "1.8", "1.8.9")
		assertSimpleOrder(t, "1.8.9", "1.9")
		assertSimpleOrder(t, "1.9", "1.9.4")
		assertSimpleOrder(t, "1.9.4", "1.10.2")
		assertSimpleOrder(t, "1.10.2", "1.11")
		assertSimpleOrder(t, "1.11", "1.11.2")
		assertSimpleOrder(t, "1.12.2", "1.15.2")
		assertSimpleOrder(t, "1.16.5", "1.17")
		assertSimpleOrder(t, "1.17", "1.17.1")
		assertSimpleOrder(t, "1.20.6", "1.21")
		assertSimpleOrder(t, "1.21", "1.21.1")
		assertSimpleOrder(t, "1.21.10", "1.21.11")
	})

	t.Run("pre-release before rc before release", func(t *testing.T) {
		assertSimpleOrder(t, "1.17-pre1", "1.17-pre2")
		assertSimpleOrder(t, "1.17-pre2", "1.17-rc1")
		assertSimpleOrder(t, "1.17-rc1", "1.17")

		assertSimpleOrder(t, "1.17.1-pre1", "1.17.1-rc1")
		assertSimpleOrder(t, "1.17.1-rc1", "1.17.1-rc2")
		assertSimpleOrder(t, "1.17.1-rc2", "1.17.1")

		assertSimpleOrder(t, "1.21.11-pre1", "1.21.11-pre2")
		assertSimpleOrder(t, "1.21.11-pre2", "1.21.11")
	})

	t.Run("pre-release of N+1 is after release of N", func(t *testing.T) {
		assertSimpleOrder(t, "1.17", "1.17.1-pre1")
		assertSimpleOrder(t, "1.21.10", "1.21.11-pre1")
	})

	t.Run("qualifiers only apply within same base version", func(t *testing.T) {
		assertSimpleOrder(t, "1.21.5-rc1", "1.21.5")
		assertSimpleOrder(t, "1.21.5", "1.21.6-pre1")
		assertSimpleOrder(t, "1.21.6-pre1", "1.21.6-rc1")
		assertSimpleOrder(t, "1.21.6-rc1", "1.21.6")
	})

	t.Run("self-equality", func(t *testing.T) {
		for _, v := range []string{"1.21.10", "1.17-pre1", "25w45a"} {
			a := domain.ParseVersionWithSchema(v, nil)
			b := domain.ParseVersionWithSchema(v, nil)
			if domain.CompareVersions(a, b) != 0 {
				t.Errorf("expected %q == %q", v, v)
			}
		}
	})
}

func TestCompareVersions_SnapshotOrdering(t *testing.T) {
	t.Parallel()

	t.Run("snapshot-N before release of same version", func(t *testing.T) {
		a := domain.ParseVersionWithSchema("26.1-snapshot-6", nil)
		b := domain.ParseVersionWithSchema("26.1", nil)
		if domain.CompareVersions(a, b) >= 0 {
			t.Errorf("expected 26.1-snapshot-6 < 26.1")
		}
	})

	t.Run("weekly snapshots compare by year and week", func(t *testing.T) {
		a := domain.ParseVersionWithSchema("25w44a", nil)
		b := domain.ParseVersionWithSchema("25w45a", nil)
		if domain.CompareVersions(a, b) >= 0 {
			t.Errorf("expected 25w44a < 25w45a")
		}
	})

	t.Run("weekly snapshot with manifest order", func(t *testing.T) {
		a := domain.ParseVersionWithSchema("25w45a", nil)
		a.ManifestOrder = 500

		b := domain.ParseVersionWithSchema("1.21.10", nil)
		b.ManifestOrder = 600

		if domain.CompareVersions(a, b) >= 0 {
			t.Errorf("expected 25w45a (manifest 500) < 1.21.10 (manifest 600)")
		}
	})
}

func TestSortVersions_FullMinecraftList(t *testing.T) {
	t.Parallel()

	input := []string{
		"1.21.11-pre2",
		"1.8",
		"1.21.10",
		"1.17-pre1",
		"1.21",
		"1.12",
		"1.17-rc1",
		"1.17",
		"1.21.6-pre1",
		"1.21.6-rc1",
		"1.21.6",
		"1.8.9",
		"1.17.1-rc1",
		"1.17.1-rc2",
		"1.17.1-pre1",
		"1.17.1",
		"1.9",
		"1.20",
		"1.21.9-rc1",
		"1.21.9",
		"1.16.5",
	}

	expected := []string{
		"1.8",
		"1.8.9",
		"1.9",
		"1.12",
		"1.16.5",
		"1.17-pre1",
		"1.17-rc1",
		"1.17",
		"1.17.1-pre1",
		"1.17.1-rc1",
		"1.17.1-rc2",
		"1.17.1",
		"1.20",
		"1.21",
		"1.21.6-pre1",
		"1.21.6-rc1",
		"1.21.6",
		"1.21.9-rc1",
		"1.21.9",
		"1.21.10",
		"1.21.11-pre2",
	}

	parsed := make([]domain.ParsedVersion, len(input))
	for i, v := range input {
		parsed[i] = domain.ParseVersionWithSchema(v, nil)
	}

	slices.SortFunc(parsed, domain.CompareVersions)

	got := make([]string, len(parsed))
	for i, pv := range parsed {
		got[i] = pv.Raw
	}

	if !slices.Equal(got, expected) {
		t.Errorf("sort order mismatch:\nwant: %v\ngot:  %v", expected, got)
	}
}

// --- Schema-driven tests ---

func assertSchemaOrder(t *testing.T, schema *domain.VersionSchema, older, newer string) {
	t.Helper()
	a := domain.ParseVersionWithSchema(older, schema)
	b := domain.ParseVersionWithSchema(newer, schema)
	if domain.CompareVersions(a, b) >= 0 {
		t.Errorf("expected %q < %q, but CompareVersions returned >= 0", older, newer)
	}
	if domain.CompareVersions(b, a) <= 0 {
		t.Errorf("expected %q > %q, but CompareVersions returned <= 0", newer, older)
	}
}

func TestParseVersionWithSchema_SpongeVanilla(t *testing.T) {
	t.Parallel()
	schema := spongeVanillaSchema()

	tests := []struct {
		raw       string
		variant   string
		mcRaw     string
		api       []int
		qualifier domain.VersionQualifier
		qualNum   int
	}{
		{raw: "1.21.10-17.0.0", variant: "current", mcRaw: "1.21.10", api: []int{17, 0, 0}, qualifier: domain.QualifierRelease},
		{raw: "1.21.10-17.0.1-RC2547", variant: "current", mcRaw: "1.21.10", api: []int{17, 0, 1}, qualifier: domain.QualifierRC, qualNum: 2547},
		{raw: "25w41a-18.0.0-RC2382", variant: "current", mcRaw: "25w41a", api: []int{18, 0, 0}, qualifier: domain.QualifierRC, qualNum: 2382},
		{raw: "26.1-snapshot-2-18.0.0-RC2531", variant: "current", mcRaw: "26.1-snapshot-2", api: []int{18, 0, 0}, qualifier: domain.QualifierRC, qualNum: 2531},
		{raw: "1.21.11-pre1-17.0.0-RC4300", variant: "current", mcRaw: "1.21.11-pre1", api: []int{17, 0, 0}, qualifier: domain.QualifierRC, qualNum: 4300},
		{raw: "1.21.11-rc1-17.0.0-RC4329", variant: "current", mcRaw: "1.21.11-rc1", api: []int{17, 0, 0}, qualifier: domain.QualifierRC, qualNum: 4329},
		{raw: "1.12.2-7.1.0-BETA-2975", variant: "beta-era", mcRaw: "1.12.2", api: []int{7, 1, 0}, qualifier: domain.QualifierRC, qualNum: 2975},
		{raw: "1.8.9-4.2.0-BETA-1874", variant: "beta-era", mcRaw: "1.8.9", api: []int{4, 2, 0}, qualifier: domain.QualifierRC, qualNum: 1874},
	}

	for _, tt := range tests {
		t.Run(tt.raw, func(t *testing.T) {
			t.Parallel()
			pv := domain.ParseVersionWithSchema(tt.raw, schema)

			if !pv.IsComposite {
				t.Fatalf("expected composite, got non-composite")
			}
			if pv.VariantName != tt.variant {
				t.Errorf("VariantName: want %q, got %q", tt.variant, pv.VariantName)
			}
			if pv.Minecraft == nil {
				t.Fatal("expected Minecraft sub-version, got nil")
			}
			if pv.Minecraft.Raw != tt.mcRaw {
				t.Errorf("Minecraft.Raw: want %q, got %q", tt.mcRaw, pv.Minecraft.Raw)
			}
			if len(pv.Segments) < 2 {
				t.Fatalf("expected at least 2 segments, got %d", len(pv.Segments))
			}
			apiSeg := pv.Segments[1]
			if !slices.Equal(apiSeg.Ints, tt.api) {
				t.Errorf("API segment: want %v, got %v", tt.api, apiSeg.Ints)
			}
			if pv.Qualifier != tt.qualifier {
				t.Errorf("Qualifier: want %d, got %d", tt.qualifier, pv.Qualifier)
			}
			if pv.QualifierNum != tt.qualNum {
				t.Errorf("QualifierNum: want %d, got %d", tt.qualNum, pv.QualifierNum)
			}
		})
	}
}

func TestParseVersionWithSchema_SpongeForge(t *testing.T) {
	t.Parallel()
	schema := spongeForgeSchema()

	tests := []struct {
		raw       string
		variant   string
		mcRaw     string
		forge     []int
		api       []int
		qualifier domain.VersionQualifier
		qualNum   int
	}{
		{raw: "1.12.2-14.23.5-7.4.8-RC4300", variant: "current", mcRaw: "1.12.2", forge: []int{14, 23, 5}, api: []int{7, 4, 8}, qualifier: domain.QualifierRC, qualNum: 4300},
		{raw: "1.12.2-14.23.5-7.4.8", variant: "current", mcRaw: "1.12.2", forge: []int{14, 23, 5}, api: []int{7, 4, 8}, qualifier: domain.QualifierRelease},
		{raw: "1.12.2-2838-7.1.0-BETA-2975", variant: "beta-era", mcRaw: "1.12.2", forge: []int{2838}, api: []int{7, 1, 0}, qualifier: domain.QualifierRC, qualNum: 2975},
		{raw: "1.8.9-1890-4.2.0-BETA-1874", variant: "beta-era", mcRaw: "1.8.9", forge: []int{1890}, api: []int{4, 2, 0}, qualifier: domain.QualifierRC, qualNum: 1874},
	}

	for _, tt := range tests {
		t.Run(tt.raw, func(t *testing.T) {
			t.Parallel()
			pv := domain.ParseVersionWithSchema(tt.raw, schema)

			if !pv.IsComposite {
				t.Fatalf("expected composite, got non-composite")
			}
			if pv.VariantName != tt.variant {
				t.Errorf("VariantName: want %q, got %q", tt.variant, pv.VariantName)
			}
			if pv.Minecraft == nil {
				t.Fatal("expected Minecraft sub-version, got nil")
			}
			if pv.Minecraft.Raw != tt.mcRaw {
				t.Errorf("Minecraft.Raw: want %q, got %q", tt.mcRaw, pv.Minecraft.Raw)
			}
			if len(pv.Segments) < 3 {
				t.Fatalf("expected 3 segments, got %d", len(pv.Segments))
			}

			forgeSeg := pv.Segments[1]
			if tt.variant == "beta-era" {
				if forgeSeg.IntValue != tt.forge[0] {
					t.Errorf("Forge IntValue: want %d, got %d", tt.forge[0], forgeSeg.IntValue)
				}
			} else {
				if !slices.Equal(forgeSeg.Ints, tt.forge) {
					t.Errorf("Forge Ints: want %v, got %v", tt.forge, forgeSeg.Ints)
				}
			}

			apiSeg := pv.Segments[2]
			if !slices.Equal(apiSeg.Ints, tt.api) {
				t.Errorf("API segment: want %v, got %v", tt.api, apiSeg.Ints)
			}
			if pv.Qualifier != tt.qualifier {
				t.Errorf("Qualifier: want %d, got %d", tt.qualifier, pv.Qualifier)
			}
			if pv.QualifierNum != tt.qualNum {
				t.Errorf("QualifierNum: want %d, got %d", tt.qualNum, pv.QualifierNum)
			}
		})
	}
}

func TestParseVersionWithSchema_SpongeNeo(t *testing.T) {
	t.Parallel()
	schema := spongeNeoSchema()

	tests := []struct {
		raw       string
		mcRaw     string
		neo       []int
		api       []int
		qualifier domain.VersionQualifier
		qualNum   int
	}{
		{raw: "1.21.10-21.4.0-11.0.0-RC100", mcRaw: "1.21.10", neo: []int{21, 4, 0}, api: []int{11, 0, 0}, qualifier: domain.QualifierRC, qualNum: 100},
		{raw: "1.21.10-21.4.0-beta-11.0.0-RC50", mcRaw: "1.21.10", neo: []int{21, 4, 0}, api: []int{11, 0, 0}, qualifier: domain.QualifierRC, qualNum: 50},
		{raw: "1.21.10-21.4.0-11.0.0", mcRaw: "1.21.10", neo: []int{21, 4, 0}, api: []int{11, 0, 0}, qualifier: domain.QualifierRelease},
		{raw: "25w41a-21.5.0-11.1.0-RC200", mcRaw: "25w41a", neo: []int{21, 5, 0}, api: []int{11, 1, 0}, qualifier: domain.QualifierRC, qualNum: 200},
	}

	for _, tt := range tests {
		t.Run(tt.raw, func(t *testing.T) {
			t.Parallel()
			pv := domain.ParseVersionWithSchema(tt.raw, schema)

			if !pv.IsComposite {
				t.Fatalf("expected composite, got non-composite")
			}
			if pv.VariantName != "current" {
				t.Errorf("VariantName: want %q, got %q", "current", pv.VariantName)
			}
			if pv.Minecraft == nil {
				t.Fatal("expected Minecraft sub-version, got nil")
			}
			if pv.Minecraft.Raw != tt.mcRaw {
				t.Errorf("Minecraft.Raw: want %q, got %q", tt.mcRaw, pv.Minecraft.Raw)
			}
			if len(pv.Segments) < 3 {
				t.Fatalf("expected 3 segments, got %d", len(pv.Segments))
			}
			neoSeg := pv.Segments[1]
			if !slices.Equal(neoSeg.Ints, tt.neo) {
				t.Errorf("Neo Ints: want %v, got %v", tt.neo, neoSeg.Ints)
			}
			apiSeg := pv.Segments[2]
			if !slices.Equal(apiSeg.Ints, tt.api) {
				t.Errorf("API Ints: want %v, got %v", tt.api, apiSeg.Ints)
			}
			if pv.Qualifier != tt.qualifier {
				t.Errorf("Qualifier: want %d, got %d", tt.qualifier, pv.Qualifier)
			}
			if pv.QualifierNum != tt.qualNum {
				t.Errorf("QualifierNum: want %d, got %d", tt.qualNum, pv.QualifierNum)
			}
		})
	}
}

func TestParseVersionWithSchema_NilSchemaFallback(t *testing.T) {
	t.Parallel()

	pv := domain.ParseVersionWithSchema("1.21.10", nil)
	if pv.IsComposite {
		t.Error("expected non-composite with nil schema")
	}
	if !slices.Equal(pv.Primary, []int{1, 21, 10}) {
		t.Errorf("Primary: want [1 21 10], got %v", pv.Primary)
	}
}

func TestParseVersionWithSchema_NoVariantMatch(t *testing.T) {
	t.Parallel()

	schema := spongeVanillaSchema()
	pv := domain.ParseVersionWithSchema("1.21.10", schema)
	if pv.IsComposite {
		t.Error("expected non-composite when no variant matches")
	}
	if !slices.Equal(pv.Primary, []int{1, 21, 10}) {
		t.Errorf("Primary: want [1 21 10], got %v", pv.Primary)
	}
}

func TestParseVersionWithSchema_MinecraftVersionString(t *testing.T) {
	t.Parallel()
	schema := spongeVanillaSchema()

	tests := []struct {
		raw  string
		want string
	}{
		{"1.21.10-17.0.0", "1.21.10"},
		{"1.21.10-17.0.1-RC2547", "1.21.10"},
		{"25w41a-18.0.0-RC2382", "25w41a"},
		{"26.1-snapshot-2-18.0.0-RC2531", "26.1-snapshot-2"},
		{"1.21.11-pre1-17.0.0-RC4300", "1.21.11-pre1"},
	}

	for _, tt := range tests {
		t.Run(tt.raw, func(t *testing.T) {
			t.Parallel()
			pv := domain.ParseVersionWithSchema(tt.raw, schema)
			got := pv.MinecraftVersionString()
			if got != tt.want {
				t.Errorf("MinecraftVersionString: want %q, got %q", tt.want, got)
			}
		})
	}
}

func TestCompareVersions_SchemaVanillaOrdering(t *testing.T) {
	t.Parallel()
	schema := spongeVanillaSchema()

	t.Run("same minecraft, RC before release", func(t *testing.T) {
		assertSchemaOrder(t, schema, "1.21.10-17.0.0-RC2392", "1.21.10-17.0.0-RC2398")
		assertSchemaOrder(t, schema, "1.21.10-17.0.0-RC2508", "1.21.10-17.0.0")
		assertSchemaOrder(t, schema, "1.21.10-17.0.0", "1.21.10-17.0.1-RC2513")
	})

	t.Run("API version ordering within same minecraft", func(t *testing.T) {
		assertSchemaOrder(t, schema, "1.21.10-17.0.0", "1.21.10-17.0.1-RC2510")
	})

	t.Run("minecraft version dominates", func(t *testing.T) {
		assertSchemaOrder(t, schema, "1.20.6-16.0.0", "1.21.10-17.0.0-RC2392")
	})

	t.Run("weekly snapshot minecraft component", func(t *testing.T) {
		assertSchemaOrder(t, schema, "25w37a-17.0.0-RC2342", "25w41a-18.0.0-RC2382")
		assertSchemaOrder(t, schema, "25w41a-18.0.0-RC2381", "25w41a-18.0.0-RC2382")
	})

	t.Run("snapshot-N minecraft component", func(t *testing.T) {
		assertSchemaOrder(t, schema, "26.1-snapshot-1-18.0.0-RC2484", "26.1-snapshot-2-18.0.0-RC2530")
		assertSchemaOrder(t, schema, "26.1-snapshot-1-18.0.0-RC2480", "26.1-snapshot-1-18.0.0-RC2484")
	})

	t.Run("minecraft pre-release component", func(t *testing.T) {
		assertSchemaOrder(t, schema, "1.21.11-pre1-17.0.0-RC4300", "1.21.11-rc1-17.0.0-RC4329")
		assertSchemaOrder(t, schema, "1.21.11-rc1-17.0.0-RC4329", "1.21.11-17.0.0-RC4400")
	})

	t.Run("beta-era ordering", func(t *testing.T) {
		assertSchemaOrder(t, schema, "1.8.9-4.2.0-BETA-1874", "1.12.2-7.1.0-BETA-2975")
		assertSchemaOrder(t, schema, "1.12.2-7.1.0-BETA-2970", "1.12.2-7.1.0-BETA-2975")
	})

	t.Run("self-equality", func(t *testing.T) {
		versions := []string{
			"1.21.10-17.0.0",
			"1.21.10-17.0.1-RC2547",
			"25w41a-18.0.0-RC2382",
			"1.12.2-7.1.0-BETA-2975",
		}
		for _, v := range versions {
			a := domain.ParseVersionWithSchema(v, schema)
			b := domain.ParseVersionWithSchema(v, schema)
			if domain.CompareVersions(a, b) != 0 {
				t.Errorf("expected %q == %q", v, v)
			}
		}
	})
}

func TestCompareVersions_SchemaForgeOrdering(t *testing.T) {
	t.Parallel()
	schema := spongeForgeSchema()

	t.Run("minecraft dominates", func(t *testing.T) {
		assertSchemaOrder(t, schema, "1.8.9-1890-4.2.0-BETA-1874", "1.12.2-2838-7.1.0-BETA-2975")
	})

	t.Run("same minecraft, forge version ordering", func(t *testing.T) {
		assertSchemaOrder(t, schema, "1.12.2-14.23.4-7.4.7-RC4200", "1.12.2-14.23.5-7.4.8-RC4300")
	})

	t.Run("same minecraft and forge, API ordering", func(t *testing.T) {
		assertSchemaOrder(t, schema, "1.12.2-14.23.5-7.4.7-RC4200", "1.12.2-14.23.5-7.4.8-RC4300")
	})

	t.Run("same everything, build number ordering", func(t *testing.T) {
		assertSchemaOrder(t, schema, "1.12.2-14.23.5-7.4.8-RC4299", "1.12.2-14.23.5-7.4.8-RC4300")
	})
}

func TestSortVersions_SchemaVanillaList(t *testing.T) {
	t.Parallel()
	schema := spongeVanillaSchema()

	input := []string{
		"25w41a-18.0.0-RC2382",
		"1.21.10-17.0.0",
		"26.1-snapshot-2-18.0.0-RC2531",
		"1.21.10-17.0.0-RC2508",
		"25w37a-17.0.0-RC2342",
		"1.21.10-17.0.1-RC2547",
		"26.1-snapshot-1-18.0.0-RC2484",
		"1.21.10-17.0.1-RC2545",
		"25w41a-18.0.0-RC2381",
		"1.21.10-17.0.0-RC2392",
	}

	expected := []string{
		"25w37a-17.0.0-RC2342",
		"25w41a-18.0.0-RC2381",
		"25w41a-18.0.0-RC2382",
		"1.21.10-17.0.0-RC2392",
		"1.21.10-17.0.0-RC2508",
		"1.21.10-17.0.0",
		"1.21.10-17.0.1-RC2545",
		"1.21.10-17.0.1-RC2547",
		"26.1-snapshot-1-18.0.0-RC2484",
		"26.1-snapshot-2-18.0.0-RC2531",
	}

	parsed := make([]domain.ParsedVersion, len(input))
	for i, v := range input {
		parsed[i] = domain.ParseVersionWithSchema(v, schema)
	}

	slices.SortFunc(parsed, domain.CompareVersions)

	got := make([]string, len(parsed))
	for i, pv := range parsed {
		got[i] = pv.Raw
	}

	if !slices.Equal(got, expected) {
		t.Errorf("sort order mismatch:\nwant: %v\ngot:  %v", expected, got)
	}
}

func TestSortVersions_SchemaForgeList(t *testing.T) {
	t.Parallel()
	schema := spongeForgeSchema()

	input := []string{
		"1.12.2-14.23.5-7.4.8-RC4300",
		"1.8.9-1890-4.2.0-BETA-1874",
		"1.12.2-14.23.5-7.4.8",
		"1.12.2-2838-7.1.0-BETA-2975",
		"1.12.2-14.23.4-7.4.7-RC4200",
		"1.12.2-14.23.5-7.4.8-RC4299",
	}

	expected := []string{
		"1.8.9-1890-4.2.0-BETA-1874",
		"1.12.2-2838-7.1.0-BETA-2975",
		"1.12.2-14.23.4-7.4.7-RC4200",
		"1.12.2-14.23.5-7.4.8-RC4299",
		"1.12.2-14.23.5-7.4.8-RC4300",
		"1.12.2-14.23.5-7.4.8",
	}

	parsed := make([]domain.ParsedVersion, len(input))
	for i, v := range input {
		parsed[i] = domain.ParseVersionWithSchema(v, schema)
	}

	slices.SortFunc(parsed, domain.CompareVersions)

	got := make([]string, len(parsed))
	for i, pv := range parsed {
		got[i] = pv.Raw
	}

	if !slices.Equal(got, expected) {
		t.Errorf("sort order mismatch:\nwant: %v\ngot:  %v", expected, got)
	}
}

// --- DEV/SNAPSHOT qualifier tests ---

func TestParseVersionWithSchema_DEVQualifier(t *testing.T) {
	t.Parallel()

	schema := &domain.VersionSchema{
		Variants: []domain.VersionFormatVariant{
			{
				Name:    "dev",
				Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<api>\d+\.\d+(?:\.\d+)?)-(?P<qualifier>DEV)-(?P<build>\d+)$`,
				Segments: []domain.SegmentRule{
					{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
					{Name: "api", ParseAs: "dotted", TagKey: "api"},
				},
			},
		},
	}

	pv := domain.ParseVersionWithSchema("1.8-2.1-DEV-128", schema)
	if !pv.IsComposite {
		t.Fatal("expected composite")
	}
	if pv.Qualifier != domain.QualifierSnapshot {
		t.Errorf("Qualifier: want QualifierSnapshot (%d), got %d", domain.QualifierSnapshot, pv.Qualifier)
	}
	if pv.QualifierNum != 128 {
		t.Errorf("QualifierNum: want 128, got %d", pv.QualifierNum)
	}
	if pv.Minecraft == nil || pv.Minecraft.Raw != "1.8" {
		t.Errorf("Minecraft.Raw: want %q, got %v", "1.8", pv.Minecraft)
	}
}

func TestParseVersionWithSchema_SNAPSHOTQualifier(t *testing.T) {
	t.Parallel()

	schema := &domain.VersionSchema{
		Variants: []domain.VersionFormatVariant{
			{
				Name:    "snapshot",
				Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<api>\d+\.\d+\.\d+)-(?P<qualifier>SNAPSHOT)$`,
				Segments: []domain.SegmentRule{
					{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
					{Name: "api", ParseAs: "dotted", TagKey: "api"},
				},
			},
		},
	}

	pv := domain.ParseVersionWithSchema("1.15.2-8.0.0-SNAPSHOT", schema)
	if !pv.IsComposite {
		t.Fatal("expected composite")
	}
	if pv.Qualifier != domain.QualifierSnapshot {
		t.Errorf("Qualifier: want QualifierSnapshot (%d), got %d", domain.QualifierSnapshot, pv.Qualifier)
	}
	if pv.QualifierNum != 0 {
		t.Errorf("QualifierNum: want 0, got %d", pv.QualifierNum)
	}
	tags := pv.ExtractTags()
	if tags["minecraft"] != "1.15.2" || tags["api"] != "8.0.0" {
		t.Errorf("unexpected tags: %v", tags)
	}
}

// --- DEV/SNAPSHOT ordering: DEV < BETA/RC < Release ---

func TestCompareVersions_QualifierOrdering_DEV_Before_BETA(t *testing.T) {
	t.Parallel()

	schema := &domain.VersionSchema{
		Variants: []domain.VersionFormatVariant{
			{
				Name:    "rc",
				Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<api>\d+\.\d+\.\d+)-(?P<qualifier>RC)(?P<build>\d+)$`,
				Segments: []domain.SegmentRule{
					{Name: "minecraft", ParseAs: "minecraft"},
					{Name: "api", ParseAs: "dotted"},
				},
			},
			{
				Name:    "beta",
				Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<api>\d+\.\d+\.\d+)-(?P<qualifier>BETA)-(?P<build>\d+)$`,
				Segments: []domain.SegmentRule{
					{Name: "minecraft", ParseAs: "minecraft"},
					{Name: "api", ParseAs: "dotted"},
				},
			},
			{
				Name:    "dev",
				Pattern: `^(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<api>\d+\.\d+\.\d+)-(?P<qualifier>DEV)-(?P<build>\d+)$`,
				Segments: []domain.SegmentRule{
					{Name: "minecraft", ParseAs: "minecraft"},
					{Name: "api", ParseAs: "dotted"},
				},
			},
		},
	}

	// DEV < BETA (same mc, same api, different qualifier)
	dev := domain.ParseVersionWithSchema("1.12.2-7.1.0-DEV-100", schema)
	beta := domain.ParseVersionWithSchema("1.12.2-7.1.0-BETA-100", schema)
	if domain.CompareVersions(dev, beta) >= 0 {
		t.Error("expected DEV < BETA")
	}

	// DEV < RC (same mc, same api)
	rc := domain.ParseVersionWithSchema("1.12.2-7.1.0-RC100", schema)
	if domain.CompareVersions(dev, rc) >= 0 {
		t.Error("expected DEV < RC")
	}
}

// --- Cross-variant ordering tests ---

func TestCompareVersions_CrossVariantOrder(t *testing.T) {
	t.Parallel()
	schema := spongeVanillaSchema()

	// Both match same MC version (1.12.2) but different variants.
	// beta-era (index 1) should sort before current (index 0).
	t.Run("beta-era sorts before current for same minecraft", func(t *testing.T) {
		betaEra := domain.ParseVersionWithSchema("1.12.2-7.1.0-BETA-2975", schema)
		current := domain.ParseVersionWithSchema("1.12.2-7.1.0-RC100", schema)

		if betaEra.VariantName != "beta-era" {
			t.Fatalf("expected beta-era variant, got %q", betaEra.VariantName)
		}
		if current.VariantName != "current" {
			t.Fatalf("expected current variant, got %q", current.VariantName)
		}

		// beta-era (VariantOrder=1) should sort before current (VariantOrder=0)
		if domain.CompareVersions(betaEra, current) >= 0 {
			t.Error("expected beta-era < current when segments match then fall to VariantOrder")
		}
	})

	t.Run("forge cross-variant: beta-era integer forge before current dotted forge", func(t *testing.T) {
		schema := spongeForgeSchema()

		betaEra := domain.ParseVersionWithSchema("1.12.2-2838-7.1.0-BETA-2975", schema)
		current := domain.ParseVersionWithSchema("1.12.2-14.23.5-7.4.8-RC4300", schema)

		// Same minecraft (1.12.2), but forge segment types differ (integer vs dotted).
		// Should fall to VariantOrder: beta-era (index 1) < current (index 0).
		if domain.CompareVersions(betaEra, current) >= 0 {
			t.Error("expected beta-era < current via VariantOrder tiebreaker")
		}
	})
}

// --- Schema validation tests ---

func TestVersionSchema_Validate(t *testing.T) {
	t.Parallel()

	t.Run("valid schema", func(t *testing.T) {
		schema := spongeVanillaSchema()
		if err := schema.Validate(); err != nil {
			t.Errorf("unexpected error: %v", err)
		}
	})

	t.Run("invalid regex", func(t *testing.T) {
		schema := &domain.VersionSchema{
			Variants: []domain.VersionFormatVariant{
				{Name: "bad", Pattern: `^(?P<unclosed`, Segments: nil},
			},
		}
		if err := schema.Validate(); err == nil {
			t.Error("expected error for invalid regex")
		}
	})

	t.Run("unknown ParseAs", func(t *testing.T) {
		schema := &domain.VersionSchema{
			Variants: []domain.VersionFormatVariant{
				{
					Name:    "test",
					Pattern: `^(?P<foo>.+)$`,
					Segments: []domain.SegmentRule{
						{Name: "foo", ParseAs: "unknown_type"},
					},
				},
			},
		}
		if err := schema.Validate(); err == nil {
			t.Error("expected error for unknown ParseAs")
		}
	})

	t.Run("invalid regex falls back gracefully in parsing", func(t *testing.T) {
		schema := &domain.VersionSchema{
			Variants: []domain.VersionFormatVariant{
				{Name: "bad", Pattern: `^(?P<unclosed`},
			},
		}
		// Even with a bad regex, ParseVersionWithSchema should not panic
		// and should fall back to simple parsing.
		pv := domain.ParseVersionWithSchema("1.21.10", schema)
		if pv.IsComposite {
			t.Error("expected non-composite fallback")
		}
		if !slices.Equal(pv.Primary, []int{1, 21, 10}) {
			t.Errorf("Primary: want [1 21 10], got %v", pv.Primary)
		}
	})
}

// --- Manifest + schema interaction test ---

func TestCompareVersions_ManifestOverridesSchemaOrdering(t *testing.T) {
	t.Parallel()
	schema := spongeVanillaSchema()

	// Without manifest, weekly snapshots sort before regular versions.
	// With manifest, manifest order dominates.
	weekly := domain.ParseVersionWithSchema("25w41a-18.0.0-RC2382", schema)
	regular := domain.ParseVersionWithSchema("1.21.10-17.0.0-RC2392", schema)

	// Without manifest: weekly < regular (natural ordering)
	if domain.CompareVersions(weekly, regular) >= 0 {
		t.Error("without manifest: expected weekly < regular")
	}

	// With manifest: weekly has higher position → should sort after regular
	weekly.ManifestOrder = 700
	weekly.Minecraft.ManifestOrder = 700
	regular.ManifestOrder = 600
	regular.Minecraft.ManifestOrder = 600

	if domain.CompareVersions(weekly, regular) <= 0 {
		t.Error("with manifest: expected weekly (700) > regular (600)")
	}
}
