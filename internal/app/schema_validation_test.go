package app_test

import (
	"errors"
	"testing"

	"github.com/spongepowered/systemofadownload/internal/app"
	"github.com/spongepowered/systemofadownload/internal/domain"
)

func TestValidateSchema(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name    string
		schema  *domain.VersionSchema
		wantErr bool
		errMsg  string
	}{
		{
			name: "valid schema with one variant",
			schema: &domain.VersionSchema{
				Variants: []domain.VersionFormatVariant{
					{
						Name:    "current",
						Pattern: `^(?P<minecraft>\d+\.\d+)-(?P<api>\d+\.\d+\.\d+)$`,
						Segments: []domain.SegmentRule{
							{Name: "minecraft", ParseAs: "minecraft", TagKey: "minecraft"},
							{Name: "api", ParseAs: "dotted", TagKey: "api"},
						},
					},
				},
			},
		},
		{
			name: "valid schema with multiple variants and all parse_as types",
			schema: &domain.VersionSchema{
				UseMojangManifest: true,
				Variants: []domain.VersionFormatVariant{
					{
						Name:    "current",
						Pattern: `^(?P<mc>\d+)-(?P<ver>\d+\.\d+)-(?P<build>\d+)-(?P<extra>.+)$`,
						Segments: []domain.SegmentRule{
							{Name: "mc", ParseAs: "minecraft"},
							{Name: "ver", ParseAs: "dotted"},
							{Name: "build", ParseAs: "integer"},
							{Name: "extra", ParseAs: "ignore"},
						},
					},
				},
			},
		},
		{
			name:    "empty variants",
			schema:  &domain.VersionSchema{Variants: []domain.VersionFormatVariant{}},
			wantErr: true,
			errMsg:  "at least one variant",
		},
		{
			name: "variant with empty name",
			schema: &domain.VersionSchema{
				Variants: []domain.VersionFormatVariant{
					{Name: "", Pattern: `^foo$`, Segments: nil},
				},
			},
			wantErr: true,
			errMsg:  "has no name",
		},
		{
			name: "variant with empty pattern",
			schema: &domain.VersionSchema{
				Variants: []domain.VersionFormatVariant{
					{Name: "test", Pattern: "", Segments: nil},
				},
			},
			wantErr: true,
			errMsg:  "has no pattern",
		},
		{
			name: "variant with invalid regex",
			schema: &domain.VersionSchema{
				Variants: []domain.VersionFormatVariant{
					{Name: "bad", Pattern: `^(?P<foo>[`, Segments: nil},
				},
			},
			wantErr: true,
			errMsg:  "invalid regex",
		},
		{
			name: "segment references non-existent capture group",
			schema: &domain.VersionSchema{
				Variants: []domain.VersionFormatVariant{
					{
						Name:    "test",
						Pattern: `^(?P<minecraft>\d+)$`,
						Segments: []domain.SegmentRule{
							{Name: "nonexistent", ParseAs: "dotted"},
						},
					},
				},
			},
			wantErr: true,
			errMsg:  "does not match a named capture group",
		},
		{
			name: "unknown parse_as value",
			schema: &domain.VersionSchema{
				Variants: []domain.VersionFormatVariant{
					{
						Name:    "test",
						Pattern: `^(?P<version>\d+)$`,
						Segments: []domain.SegmentRule{
							{Name: "version", ParseAs: "unknown"},
						},
					},
				},
			},
			wantErr: true,
			errMsg:  "unknown parse_as",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			err := app.ValidateSchema(tt.schema)
			if tt.wantErr {
				if err == nil {
					t.Fatal("expected error, got nil")
				}
				if !errors.Is(err, app.ErrInvalidSchema) {
					t.Errorf("expected ErrInvalidSchema, got: %v", err)
				}
				if tt.errMsg != "" && !containsSubstring(err.Error(), tt.errMsg) {
					t.Errorf("error %q should contain %q", err.Error(), tt.errMsg)
				}
				return
			}
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
		})
	}
}

func containsSubstring(s, sub string) bool {
	return len(s) >= len(sub) && (s == sub || len(s) > 0 && containsAt(s, sub))
}

func containsAt(s, sub string) bool {
	for i := 0; i <= len(s)-len(sub); i++ {
		if s[i:i+len(sub)] == sub {
			return true
		}
	}
	return false
}
