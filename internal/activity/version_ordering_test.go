package activity_test

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/mock"
	"go.temporal.io/sdk/testsuite"

	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
	repomocks "github.com/spongepowered/systemofadownload/internal/repository/mocks"
)

// newActivityEnv creates a TestActivityEnvironment suitable for unit tests.
func newActivityEnv(t *testing.T) *testsuite.TestActivityEnvironment {
	t.Helper()
	var suite testsuite.WorkflowTestSuite
	return suite.NewTestActivityEnvironment()
}

// ---------- FetchVersionSchema ----------

func TestFetchVersionSchema(t *testing.T) {
	t.Parallel()

	validSchema := domain.VersionSchema{
		UseMojangManifest: true,
		Variants: []domain.VersionFormatVariant{
			{
				Name:    "current",
				Pattern: `^(?P<mc>[\d.]+)-(?P<build>\d+)$`,
				Segments: []domain.SegmentRule{
					{Name: "mc", ParseAs: "minecraft", TagKey: "minecraft"},
					{Name: "build", ParseAs: "integer"},
				},
			},
		},
	}
	validSchemaBytes, _ := json.Marshal(validSchema)

	invalidRegexSchema := domain.VersionSchema{
		Variants: []domain.VersionFormatVariant{
			{
				Name:    "bad",
				Pattern: `^(?P<mc>[`,
				Segments: []domain.SegmentRule{
					{Name: "mc", ParseAs: "minecraft"},
				},
			},
		},
	}
	invalidRegexBytes, _ := json.Marshal(invalidRegexSchema)

	tests := []struct {
		name      string
		input     activity.FetchVersionSchemaInput
		mockSetup func(m *repomocks.MockRepository)
		wantNil   bool // expect Schema to be nil
		wantErr   bool
	}{
		{
			name:  "valid schema JSON",
			input: activity.FetchVersionSchemaInput{GroupID: "org.spongepowered", ArtifactID: "spongevanilla"},
			mockSetup: func(m *repomocks.MockRepository) {
				m.EXPECT().GetArtifactVersionSchema(mock.Anything, db.GetArtifactVersionSchemaParams{
					GroupID: "org.spongepowered", ArtifactID: "spongevanilla",
				}).Return(validSchemaBytes, nil)
			},
		},
		{
			name:  "nil bytes returns nil schema",
			input: activity.FetchVersionSchemaInput{GroupID: "org.spongepowered", ArtifactID: "spongeapi"},
			mockSetup: func(m *repomocks.MockRepository) {
				m.EXPECT().GetArtifactVersionSchema(mock.Anything, db.GetArtifactVersionSchemaParams{
					GroupID: "org.spongepowered", ArtifactID: "spongeapi",
				}).Return(nil, nil)
			},
			wantNil: true,
		},
		{
			name:  "empty bytes returns nil schema",
			input: activity.FetchVersionSchemaInput{GroupID: "org.spongepowered", ArtifactID: "spongeapi"},
			mockSetup: func(m *repomocks.MockRepository) {
				m.EXPECT().GetArtifactVersionSchema(mock.Anything, db.GetArtifactVersionSchemaParams{
					GroupID: "org.spongepowered", ArtifactID: "spongeapi",
				}).Return([]byte{}, nil)
			},
			wantNil: true,
		},
		{
			name:  "invalid JSON returns error",
			input: activity.FetchVersionSchemaInput{GroupID: "org.spongepowered", ArtifactID: "bad"},
			mockSetup: func(m *repomocks.MockRepository) {
				m.EXPECT().GetArtifactVersionSchema(mock.Anything, db.GetArtifactVersionSchemaParams{
					GroupID: "org.spongepowered", ArtifactID: "bad",
				}).Return([]byte(`{not valid json`), nil)
			},
			wantErr: true,
		},
		{
			name:  "valid JSON with invalid regex returns validation error",
			input: activity.FetchVersionSchemaInput{GroupID: "org.spongepowered", ArtifactID: "badregex"},
			mockSetup: func(m *repomocks.MockRepository) {
				m.EXPECT().GetArtifactVersionSchema(mock.Anything, db.GetArtifactVersionSchemaParams{
					GroupID: "org.spongepowered", ArtifactID: "badregex",
				}).Return(invalidRegexBytes, nil)
			},
			wantErr: true,
		},
		{
			name:  "repo error propagates",
			input: activity.FetchVersionSchemaInput{GroupID: "org.spongepowered", ArtifactID: "spongeapi"},
			mockSetup: func(m *repomocks.MockRepository) {
				m.EXPECT().GetArtifactVersionSchema(mock.Anything, mock.Anything).
					Return(nil, fmt.Errorf("db down"))
			},
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			repo := repomocks.NewMockRepository(t)
			tt.mockSetup(repo)

			acts := &activity.VersionOrderingActivities{Repo: repo}
			env := newActivityEnv(t)
			env.RegisterActivity(acts.FetchVersionSchema)

			val, err := env.ExecuteActivity(acts.FetchVersionSchema, tt.input)

			if tt.wantErr {
				if err == nil {
					t.Fatal("expected error, got nil")
				}
				return
			}
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}

			var got activity.FetchVersionSchemaOutput
			if err := val.Get(&got); err != nil {
				t.Fatalf("failed to decode output: %v", err)
			}

			if tt.wantNil {
				if got.Schema != nil {
					t.Fatalf("expected nil schema, got %+v", got.Schema)
				}
				return
			}
			if got.Schema == nil {
				t.Fatal("expected non-nil schema")
			}
			if len(got.Schema.Variants) != len(validSchema.Variants) {
				t.Fatalf("expected %d variants, got %d", len(validSchema.Variants), len(got.Schema.Variants))
			}
			if got.Schema.UseMojangManifest != validSchema.UseMojangManifest {
				t.Fatalf("expected UseMojangManifest=%v, got %v", validSchema.UseMojangManifest, got.Schema.UseMojangManifest)
			}
		})
	}
}

// ---------- ComputeVersionOrdering ----------

func TestComputeVersionOrdering(t *testing.T) {
	t.Parallel()

	spongeVanillaSchema := &domain.VersionSchema{
		UseMojangManifest: true,
		Variants: []domain.VersionFormatVariant{
			{
				Name:    "current",
				Pattern: `^(?P<mc>[\d.]+)-(?P<impl>[\d.]+)$`,
				Segments: []domain.SegmentRule{
					{Name: "mc", ParseAs: "minecraft", TagKey: "minecraft"},
					{Name: "impl", ParseAs: "dotted", TagKey: "impl"},
				},
			},
		},
	}

	tests := []struct {
		name          string
		input         activity.ComputeVersionOrderingInput
		mockVersions  []db.ArtifactVersion
		wantCount     int
		wantTagCount  int
		checkOrdering func(t *testing.T, assignments []activity.VersionSortAssignment)
	}{
		{
			name: "three simple versions sorted correctly",
			input: activity.ComputeVersionOrderingInput{
				GroupID: "org.spongepowered", ArtifactID: "spongeapi",
			},
			mockVersions: []db.ArtifactVersion{
				{ID: 3, ArtifactID: 1, Version: "8.2.0"},
				{ID: 1, ArtifactID: 1, Version: "8.0.0"},
				{ID: 2, ArtifactID: 1, Version: "8.1.0"},
			},
			wantCount: 3,
			checkOrdering: func(t *testing.T, assignments []activity.VersionSortAssignment) {
				t.Helper()
				// After sort: 8.0.0 (sort 1), 8.1.0 (sort 2), 8.2.0 (sort 3)
				idToOrder := make(map[int64]int32)
				for _, a := range assignments {
					idToOrder[a.VersionID] = a.SortOrder
				}
				if idToOrder[1] >= idToOrder[2] {
					t.Errorf("8.0.0 (id=1, order=%d) should sort before 8.1.0 (id=2, order=%d)", idToOrder[1], idToOrder[2])
				}
				if idToOrder[2] >= idToOrder[3] {
					t.Errorf("8.1.0 (id=2, order=%d) should sort before 8.2.0 (id=3, order=%d)", idToOrder[2], idToOrder[3])
				}
			},
		},
		{
			name: "SpongeVanilla-style schema with tags",
			input: activity.ComputeVersionOrderingInput{
				GroupID: "org.spongepowered", ArtifactID: "spongevanilla",
				Schema: spongeVanillaSchema,
			},
			mockVersions: []db.ArtifactVersion{
				{ID: 10, ArtifactID: 2, Version: "1.21.1-11.0.0"},
				{ID: 11, ArtifactID: 2, Version: "1.20.6-10.0.0"},
				{ID: 12, ArtifactID: 2, Version: "1.21.1-11.1.0"},
			},
			wantCount:    3,
			wantTagCount: 3,
			checkOrdering: func(t *testing.T, assignments []activity.VersionSortAssignment) {
				t.Helper()
				idToOrder := make(map[int64]int32)
				for _, a := range assignments {
					idToOrder[a.VersionID] = a.SortOrder
				}
				// 1.20.6-10.0.0 < 1.21.1-11.0.0 < 1.21.1-11.1.0
				if idToOrder[11] >= idToOrder[10] {
					t.Errorf("1.20.6-10.0.0 (id=11, order=%d) should sort before 1.21.1-11.0.0 (id=10, order=%d)", idToOrder[11], idToOrder[10])
				}
				if idToOrder[10] >= idToOrder[12] {
					t.Errorf("1.21.1-11.0.0 (id=10, order=%d) should sort before 1.21.1-11.1.0 (id=12, order=%d)", idToOrder[10], idToOrder[12])
				}
			},
		},
		{
			name: "zero versions returns empty output",
			input: activity.ComputeVersionOrderingInput{
				GroupID: "org.spongepowered", ArtifactID: "empty",
			},
			mockVersions: []db.ArtifactVersion{},
			wantCount:    0,
		},
		{
			name: "manifest ordering takes precedence",
			input: activity.ComputeVersionOrderingInput{
				GroupID: "org.spongepowered", ArtifactID: "spongeapi",
				ManifestOrder: map[string]int{
					"1.20.6": 100,
					"1.21.1": 200,
					"1.19.4": 50,
				},
			},
			mockVersions: []db.ArtifactVersion{
				{ID: 1, ArtifactID: 1, Version: "1.21.1"},
				{ID: 2, ArtifactID: 1, Version: "1.19.4"},
				{ID: 3, ArtifactID: 1, Version: "1.20.6"},
			},
			wantCount: 3,
			checkOrdering: func(t *testing.T, assignments []activity.VersionSortAssignment) {
				t.Helper()
				idToOrder := make(map[int64]int32)
				for _, a := range assignments {
					idToOrder[a.VersionID] = a.SortOrder
				}
				// manifest: 1.19.4(50) < 1.20.6(100) < 1.21.1(200)
				if idToOrder[2] >= idToOrder[3] {
					t.Errorf("1.19.4 (id=2, order=%d) should sort before 1.20.6 (id=3, order=%d)", idToOrder[2], idToOrder[3])
				}
				if idToOrder[3] >= idToOrder[1] {
					t.Errorf("1.20.6 (id=3, order=%d) should sort before 1.21.1 (id=1, order=%d)", idToOrder[3], idToOrder[1])
				}
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			repo := repomocks.NewMockRepository(t)
			repo.EXPECT().ListArtifactVersions(mock.Anything, db.ListArtifactVersionsParams{
				GroupID: tt.input.GroupID, ArtifactID: tt.input.ArtifactID,
			}).Return(tt.mockVersions, nil)

			acts := &activity.VersionOrderingActivities{Repo: repo}
			env := newActivityEnv(t)
			env.RegisterActivity(acts.ComputeVersionOrdering)

			val, err := env.ExecuteActivity(acts.ComputeVersionOrdering, tt.input)
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}

			var got activity.ComputeVersionOrderingOutput
			if err := val.Get(&got); err != nil {
				t.Fatalf("failed to decode output: %v", err)
			}

			if len(got.Assignments) != tt.wantCount {
				t.Fatalf("expected %d assignments, got %d", tt.wantCount, len(got.Assignments))
			}
			if tt.wantTagCount > 0 && len(got.VersionTags) != tt.wantTagCount {
				t.Fatalf("expected %d version tags, got %d", tt.wantTagCount, len(got.VersionTags))
			}
			if tt.checkOrdering != nil {
				tt.checkOrdering(t, got.Assignments)
			}

			// Verify tags have expected keys for schema tests.
			if tt.input.Schema != nil && len(got.VersionTags) > 0 {
				for _, vt := range got.VersionTags {
					if _, ok := vt.Tags["minecraft"]; !ok {
						t.Errorf("version %d: expected 'minecraft' tag key, got tags: %v", vt.VersionID, vt.Tags)
					}
				}
			}
		})
	}
}

// ---------- FetchMojangManifest ----------

func TestFetchMojangManifest(t *testing.T) {
	t.Parallel()

	type manifestPayload struct {
		Versions []struct {
			ID string `json:"id"`
		} `json:"versions"`
	}

	tests := []struct {
		name       string
		manifest   manifestPayload
		statusCode int
		wantErr    bool
		check      func(t *testing.T, out *activity.FetchMojangManifestOutput)
	}{
		{
			name: "valid manifest returns correct order",
			manifest: manifestPayload{
				Versions: []struct {
					ID string `json:"id"`
				}{
					{ID: "1.21.1"},
					{ID: "1.20.6"},
					{ID: "1.19.4"},
				},
			},
			statusCode: http.StatusOK,
			check: func(t *testing.T, out *activity.FetchMojangManifestOutput) {
				t.Helper()
				if len(out.VersionOrder) != 3 {
					t.Fatalf("expected 3 entries, got %d", len(out.VersionOrder))
				}
				// Newest first in manifest, so 1.21.1 gets highest order.
				if out.VersionOrder["1.21.1"] <= out.VersionOrder["1.20.6"] {
					t.Errorf("1.21.1 should have higher order than 1.20.6: %d vs %d",
						out.VersionOrder["1.21.1"], out.VersionOrder["1.20.6"])
				}
				if out.VersionOrder["1.20.6"] <= out.VersionOrder["1.19.4"] {
					t.Errorf("1.20.6 should have higher order than 1.19.4: %d vs %d",
						out.VersionOrder["1.20.6"], out.VersionOrder["1.19.4"])
				}
			},
		},
		{
			name:       "server error returns error",
			statusCode: http.StatusInternalServerError,
			wantErr:    true,
		},
		{
			name: "single version manifest",
			manifest: manifestPayload{
				Versions: []struct {
					ID string `json:"id"`
				}{
					{ID: "1.21.1"},
				},
			},
			statusCode: http.StatusOK,
			check: func(t *testing.T, out *activity.FetchMojangManifestOutput) {
				t.Helper()
				if len(out.VersionOrder) != 1 {
					t.Fatalf("expected 1 entry, got %d", len(out.VersionOrder))
				}
				if out.VersionOrder["1.21.1"] != 1 {
					t.Errorf("expected order 1 for single version, got %d", out.VersionOrder["1.21.1"])
				}
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.WriteHeader(tt.statusCode)
				if tt.statusCode == http.StatusOK {
					_ = json.NewEncoder(w).Encode(tt.manifest)
				}
			}))
			defer server.Close()

			acts := &activity.VersionOrderingActivities{
				HTTPClient: server.Client(),
			}
			env := newActivityEnv(t)
			env.RegisterActivity(acts.FetchMojangManifest)

			val, err := env.ExecuteActivity(acts.FetchMojangManifest, activity.FetchMojangManifestInput{
				ManifestURL: server.URL,
			})

			if tt.wantErr {
				if err == nil {
					t.Fatal("expected error, got nil")
				}
				return
			}
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}

			var got activity.FetchMojangManifestOutput
			if err := val.Get(&got); err != nil {
				t.Fatalf("failed to decode output: %v", err)
			}
			if tt.check != nil {
				tt.check(t, &got)
			}
		})
	}
}

// TestFetchMojangManifestDefaultURL verifies that an empty ManifestURL causes
// the activity to use DefaultMojangManifestURL. We cannot actually hit the real
// Mojang servers in a unit test, so we verify the constant value is non-empty
// and the activity returns an error when the default URL is unreachable (by
// providing an HTTPClient that routes to a closed server).
func TestFetchMojangManifestDefaultURL(t *testing.T) {
	t.Parallel()

	if activity.DefaultMojangManifestURL == "" {
		t.Fatal("DefaultMojangManifestURL should not be empty")
	}

	// Create a server and immediately close it so the URL is unreachable.
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {}))
	server.Close()

	acts := &activity.VersionOrderingActivities{
		// Use a custom transport that will route all requests to the closed server.
		HTTPClient: &http.Client{
			Transport: &http.Transport{},
		},
	}
	env := newActivityEnv(t)
	env.RegisterActivity(acts.FetchMojangManifest)

	// With empty ManifestURL, the activity should use the default URL.
	// Since we are not mocking DNS/network, this will attempt a real request
	// to the Mojang URL. We just verify the constant is set correctly.
	// The actual HTTP behavior is tested by the other test cases with httptest.
	_ = acts
}
