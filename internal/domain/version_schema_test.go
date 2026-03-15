package domain_test

import (
	"testing"

	"github.com/google/go-cmp/cmp"
	"github.com/spongepowered/systemofadownload/internal/domain"
)

func TestExtractTags(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name   string
		raw    string
		schema *domain.VersionSchema
		want   map[string]string
	}{
		{
			name: "SpongeVanilla current format",
			raw:  "1.21.10-17.0.1-RC2547",
			schema: &domain.VersionSchema{
				Variants: []domain.VersionFormatVariant{
					{
						Name:    "current",
						Pattern: `^(?P<minecraft>` + mcPattern + `)-(?P<api>\d+\.\d+\.\d+)(?:-(?P<qualifier>RC)(?P<build>\d+))?$`,
						Segments: []domain.SegmentRule{
							{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
							{Name: "api", ParseAs: "dotted", TagKey: "api"},
						},
					},
				},
			},
			want: map[string]string{
				"minecraft": "1.21.10",
				"api":       "17.0.1",
			},
		},
		{
			name: "SpongeForge 3-segment format",
			raw:  "1.12.2-2838-7.4.8-RC4300",
			schema: &domain.VersionSchema{
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
				},
			},
			want: map[string]string{
				"minecraft": "1.12.2",
				"forge":     "2838",
				"api":       "7.4.8",
			},
		},
		{
			name: "SpongeNeo with beta",
			raw:  "1.21.10-21.4.0-beta-11.0.0-RC100",
			schema: &domain.VersionSchema{
				Variants: []domain.VersionFormatVariant{
					{
						Name:    "neo-beta",
						Pattern: `^(?P<minecraft>` + mcPattern + `)-(?P<neo>\d+\.\d+\.\d+)-beta-(?P<api>\d+\.\d+\.\d+)(?:-(?P<qualifier>RC)(?P<build>\d+))?$`,
						Segments: []domain.SegmentRule{
							{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
							{Name: "neo", ParseAs: "dotted", TagKey: "neo"},
							{Name: "api", ParseAs: "dotted", TagKey: "api"},
						},
					},
				},
			},
			want: map[string]string{
				"minecraft": "1.21.10",
				"neo":       "21.4.0",
				"api":       "11.0.0",
			},
		},
		{
			name: "no tags configured",
			raw:  "1.21.10-17.0.1-RC2547",
			schema: &domain.VersionSchema{
				Variants: []domain.VersionFormatVariant{
					{
						Name:    "current",
						Pattern: `^(?P<minecraft>` + mcPattern + `)-(?P<api>\d+\.\d+\.\d+)(?:-(?P<qualifier>RC)(?P<build>\d+))?$`,
						Segments: []domain.SegmentRule{
							{Name: "minecraft", ParseAs: "minecraft"},
							{Name: "api", ParseAs: "dotted"},
						},
					},
				},
			},
			want: nil,
		},
		{
			name:   "nil schema",
			raw:    "1.21.10-17.0.1-RC2547",
			schema: nil,
			want:   nil,
		},
		{
			name: "partial tags — only minecraft tagged",
			raw:  "1.21.10-17.0.1-RC2547",
			schema: &domain.VersionSchema{
				Variants: []domain.VersionFormatVariant{
					{
						Name:    "current",
						Pattern: `^(?P<minecraft>` + mcPattern + `)-(?P<api>\d+\.\d+\.\d+)(?:-(?P<qualifier>RC)(?P<build>\d+))?$`,
						Segments: []domain.SegmentRule{
							{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
							{Name: "api", ParseAs: "dotted"},
						},
					},
				},
			},
			want: map[string]string{
				"minecraft": "1.21.10",
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			pv := domain.ParseVersionWithSchema(tt.raw, tt.schema)
			got := pv.ExtractTags()
			if diff := cmp.Diff(tt.want, got); diff != "" {
				t.Errorf("ExtractTags() mismatch (-want +got):\n%s", diff)
			}
		})
	}
}
