package domain_test

import (
	"slices"
	"testing"

	"github.com/spongepowered/systemofadownload/internal/domain"
)

func TestParseVersion_Simple(t *testing.T) {
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
			pv := domain.ParseVersion(tt.raw)

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

func TestParseVersion_Composite(t *testing.T) {
	t.Parallel()

	tests := []struct {
		raw       string
		mcRaw     string // expected minecraft sub-version raw string
		api       []int
		qualifier domain.VersionQualifier
		qualNum   int
	}{
		// Standard SpongeVanilla release
		{raw: "1.21.10-17.0.0", mcRaw: "1.21.10", api: []int{17, 0, 0}, qualifier: domain.QualifierRelease},
		// Standard SpongeVanilla RC
		{raw: "1.21.10-17.0.1-RC2547", mcRaw: "1.21.10", api: []int{17, 0, 1}, qualifier: domain.QualifierRC, qualNum: 2547},
		// Weekly snapshot as minecraft component
		{raw: "25w41a-18.0.0-RC2382", mcRaw: "25w41a", api: []int{18, 0, 0}, qualifier: domain.QualifierRC, qualNum: 2382},
		{raw: "25w37a-17.0.0-RC2342", mcRaw: "25w37a", api: []int{17, 0, 0}, qualifier: domain.QualifierRC, qualNum: 2342},
		// Dash-containing snapshot as minecraft component
		{raw: "26.1-snapshot-2-18.0.0-RC2531", mcRaw: "26.1-snapshot-2", api: []int{18, 0, 0}, qualifier: domain.QualifierRC, qualNum: 2531},
		{raw: "26.1-snapshot-1-18.0.0-RC2484", mcRaw: "26.1-snapshot-1", api: []int{18, 0, 0}, qualifier: domain.QualifierRC, qualNum: 2484},
		// Minecraft pre-release as minecraft component
		{raw: "1.21.11-rc1-17.0.0-RC4329", mcRaw: "1.21.11-rc1", api: []int{17, 0, 0}, qualifier: domain.QualifierRC, qualNum: 4329},
		{raw: "1.21.11-pre1-17.0.0-RC4300", mcRaw: "1.21.11-pre1", api: []int{17, 0, 0}, qualifier: domain.QualifierRC, qualNum: 4300},
	}

	for _, tt := range tests {
		t.Run(tt.raw, func(t *testing.T) {
			t.Parallel()
			pv := domain.ParseVersion(tt.raw)

			if !pv.IsComposite {
				t.Fatalf("expected composite, got non-composite")
			}
			if pv.Minecraft == nil {
				t.Fatal("expected Minecraft sub-version, got nil")
			}
			if pv.Minecraft.Raw != tt.mcRaw {
				t.Errorf("Minecraft.Raw: want %q, got %q", tt.mcRaw, pv.Minecraft.Raw)
			}
			if !slices.Equal(pv.APIVersion, tt.api) {
				t.Errorf("APIVersion: want %v, got %v", tt.api, pv.APIVersion)
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

func TestParseVersion_MinecraftVersionString(t *testing.T) {
	t.Parallel()

	tests := []struct {
		raw  string
		want string
	}{
		{"1.21.10", "1.21.10"},
		{"1.21.10-pre1", "1.21.10-pre1"},
		{"25w45a", "25w45a"},
		{"1.21.10-17.0.0", "1.21.10"},
		{"1.21.10-17.0.1-RC2547", "1.21.10"},
		{"25w41a-18.0.0-RC2382", "25w41a"},
		{"26.1-snapshot-2-18.0.0-RC2531", "26.1-snapshot-2"},
	}

	for _, tt := range tests {
		t.Run(tt.raw, func(t *testing.T) {
			t.Parallel()
			pv := domain.ParseVersion(tt.raw)
			got := pv.MinecraftVersionString()
			if got != tt.want {
				t.Errorf("MinecraftVersionString: want %q, got %q", tt.want, got)
			}
		})
	}
}

func assertOrder(t *testing.T, older, newer string) {
	t.Helper()
	a := domain.ParseVersion(older)
	b := domain.ParseVersion(newer)
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
		assertOrder(t, "1.8", "1.8.9")
		assertOrder(t, "1.8.9", "1.9")
		assertOrder(t, "1.9", "1.9.4")
		assertOrder(t, "1.9.4", "1.10.2")
		assertOrder(t, "1.10.2", "1.11")
		assertOrder(t, "1.11", "1.11.2")
		assertOrder(t, "1.12.2", "1.15.2")
		assertOrder(t, "1.16.5", "1.17")
		assertOrder(t, "1.17", "1.17.1")
		assertOrder(t, "1.20.6", "1.21")
		assertOrder(t, "1.21", "1.21.1")
		assertOrder(t, "1.21.10", "1.21.11")
	})

	t.Run("pre-release before rc before release", func(t *testing.T) {
		assertOrder(t, "1.17-pre1", "1.17-pre2")
		assertOrder(t, "1.17-pre2", "1.17-rc1")
		assertOrder(t, "1.17-rc1", "1.17")

		assertOrder(t, "1.17.1-pre1", "1.17.1-rc1")
		assertOrder(t, "1.17.1-rc1", "1.17.1-rc2")
		assertOrder(t, "1.17.1-rc2", "1.17.1")

		assertOrder(t, "1.21.11-pre1", "1.21.11-pre2")
		assertOrder(t, "1.21.11-pre2", "1.21.11")
	})

	t.Run("pre-release of N+1 is after release of N", func(t *testing.T) {
		assertOrder(t, "1.17", "1.17.1-pre1")
		assertOrder(t, "1.21.10", "1.21.11-pre1")
	})

	t.Run("qualifiers only apply within same base version", func(t *testing.T) {
		assertOrder(t, "1.21.5-rc1", "1.21.5")
		assertOrder(t, "1.21.5", "1.21.6-pre1")
		assertOrder(t, "1.21.6-pre1", "1.21.6-rc1")
		assertOrder(t, "1.21.6-rc1", "1.21.6")
	})

	t.Run("self-equality", func(t *testing.T) {
		for _, v := range []string{"1.21.10", "1.17-pre1", "25w45a"} {
			a := domain.ParseVersion(v)
			b := domain.ParseVersion(v)
			if domain.CompareVersions(a, b) != 0 {
				t.Errorf("expected %q == %q", v, v)
			}
		}
	})
}

func TestCompareVersions_SpongeVanillaOrdering(t *testing.T) {
	t.Parallel()

	t.Run("same minecraft, RC before release", func(t *testing.T) {
		assertOrder(t, "1.21.10-17.0.0-RC2392", "1.21.10-17.0.0-RC2398")
		assertOrder(t, "1.21.10-17.0.0-RC2508", "1.21.10-17.0.0")
		assertOrder(t, "1.21.10-17.0.0", "1.21.10-17.0.1-RC2513")
	})

	t.Run("API version ordering within same minecraft", func(t *testing.T) {
		assertOrder(t, "1.21.10-17.0.0", "1.21.10-17.0.1-RC2510")
	})

	t.Run("minecraft version dominates", func(t *testing.T) {
		assertOrder(t, "1.20.6-16.0.0", "1.21.10-17.0.0-RC2392")
	})

	t.Run("RC build number ordering", func(t *testing.T) {
		assertOrder(t, "1.21.10-17.0.1-RC2545", "1.21.10-17.0.1-RC2546")
		assertOrder(t, "1.21.10-17.0.1-RC2546", "1.21.10-17.0.1-RC2547")
	})

	t.Run("weekly snapshot minecraft component", func(t *testing.T) {
		// 25w37a builds come before 25w41a builds (week 37 < week 41)
		assertOrder(t, "25w37a-17.0.0-RC2342", "25w41a-18.0.0-RC2382")
		// Within same weekly snapshot, build number matters
		assertOrder(t, "25w41a-18.0.0-RC2381", "25w41a-18.0.0-RC2382")
	})

	t.Run("snapshot-N minecraft component", func(t *testing.T) {
		// 26.1-snapshot-1 before 26.1-snapshot-2
		assertOrder(t, "26.1-snapshot-1-18.0.0-RC2484", "26.1-snapshot-2-18.0.0-RC2530")
		// Within same snapshot, build number matters
		assertOrder(t, "26.1-snapshot-1-18.0.0-RC2480", "26.1-snapshot-1-18.0.0-RC2484")
	})

	t.Run("minecraft pre-release component", func(t *testing.T) {
		// pre1 builds come before rc1 builds of the same minecraft version
		assertOrder(t, "1.21.11-pre1-17.0.0-RC4300", "1.21.11-rc1-17.0.0-RC4329")
		// rc1 builds come before release builds
		assertOrder(t, "1.21.11-rc1-17.0.0-RC4329", "1.21.11-17.0.0-RC4400")
		// Within same minecraft pre-release, build number matters
		assertOrder(t, "1.21.11-rc1-17.0.0-RC4328", "1.21.11-rc1-17.0.0-RC4329")
	})

	t.Run("cross-format ordering", func(t *testing.T) {
		// Standard minecraft before weekly snapshot (without manifest,
		// weekly snapshots sort below regular versions)
		// With manifest they'd be properly placed.
		// 25w37a weekly → sorts below 1.21.10 release when no manifest
		assertOrder(t, "25w37a-17.0.0-RC2342", "1.21.10-17.0.0-RC2398")
	})
}

func TestCompareVersions_SnapshotOrdering(t *testing.T) {
	t.Parallel()

	t.Run("snapshot-N before release of same version", func(t *testing.T) {
		a := domain.ParseVersion("26.1-snapshot-6")
		b := domain.ParseVersion("26.1")
		if domain.CompareVersions(a, b) >= 0 {
			t.Errorf("expected 26.1-snapshot-6 < 26.1")
		}
	})

	t.Run("weekly snapshots compare by year and week", func(t *testing.T) {
		a := domain.ParseVersion("25w44a")
		b := domain.ParseVersion("25w45a")
		if domain.CompareVersions(a, b) >= 0 {
			t.Errorf("expected 25w44a < 25w45a")
		}
	})

	t.Run("weekly snapshot with manifest order", func(t *testing.T) {
		a := domain.ParseVersion("25w45a")
		a.ManifestOrder = 500

		b := domain.ParseVersion("1.21.10")
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
		parsed[i] = domain.ParseVersion(v)
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

func TestSortVersions_SpongeVanillaList(t *testing.T) {
	t.Parallel()

	// A representative slice of SpongeVanilla versions, shuffled.
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

	// Expected ascending (oldest → newest).
	// Weekly snapshots (25wXXa) sort before regular versions when no manifest.
	// Within same type, normal ordering applies.
	expected := []string{
		// Weekly snapshots first (no manifest), ordered by week
		"25w37a-17.0.0-RC2342",
		"25w41a-18.0.0-RC2381",
		"25w41a-18.0.0-RC2382",
		// Then standard minecraft versions
		"1.21.10-17.0.0-RC2392",
		"1.21.10-17.0.0-RC2508",
		"1.21.10-17.0.0",
		"1.21.10-17.0.1-RC2545",
		"1.21.10-17.0.1-RC2547",
		// snapshot-N minecraft versions (26.1-snapshot-N < 26.1 release, and 26 > 1)
		"26.1-snapshot-1-18.0.0-RC2484",
		"26.1-snapshot-2-18.0.0-RC2531",
	}

	parsed := make([]domain.ParsedVersion, len(input))
	for i, v := range input {
		parsed[i] = domain.ParseVersion(v)
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
