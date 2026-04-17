package httpapi

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"slices"
	"strings"
	"testing"

	"github.com/jackc/pgx/v5"
	"go.temporal.io/api/serviceerror"
	"go.temporal.io/sdk/client"

	"github.com/spongepowered/systemofadownload/api"
	"github.com/spongepowered/systemofadownload/internal/app"
	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/repository"
)

type mockQuerier struct {
	db.Querier
	groups    map[string]db.Group
	artifacts map[string]db.Artifact // key: "groupID:artifactID"
	versions  map[int64]db.ArtifactVersion
	tags      map[int64][]db.ArtifactVersionedTag
	assets    map[int64][]db.ArtifactVersionedAsset
}

func (m *mockQuerier) WithTx(ctx context.Context, fn func(repository.Tx) error) error {
	// For testing, just call the function with the same querier
	return fn(m)
}

func (m *mockQuerier) GroupExistsByMavenID(ctx context.Context, mavenID string) (bool, error) {
	_, ok := m.groups[mavenID]
	return ok, nil
}

func (m *mockQuerier) GetGroup(ctx context.Context, mavenID string) (db.Group, error) {
	g, ok := m.groups[mavenID]
	if !ok {
		return db.Group{}, pgx.ErrNoRows
	}
	return g, nil
}

func (m *mockQuerier) ListGroups(ctx context.Context) ([]db.Group, error) {
	var groups []db.Group
	for _, g := range m.groups {
		groups = append(groups, g)
	}
	return groups, nil
}

func (m *mockQuerier) CreateGroup(ctx context.Context, arg db.CreateGroupParams) (db.Group, error) {
	g := db.Group(arg)
	m.groups[arg.MavenID] = g
	return g, nil
}

func (m *mockQuerier) GetArtifactByGroupAndId(ctx context.Context, arg db.GetArtifactByGroupAndIdParams) (db.Artifact, error) {
	key := arg.GroupID + ":" + arg.ArtifactID
	a, ok := m.artifacts[key]
	if !ok {
		return db.Artifact{}, pgx.ErrNoRows
	}
	return a, nil
}

func (m *mockQuerier) CreateArtifact(ctx context.Context, arg db.CreateArtifactParams) (db.Artifact, error) { //nolint:gocritic // interface implementation
	key := arg.GroupID + ":" + arg.ArtifactID
	a := db.Artifact{
		ID:              int64(len(m.artifacts) + 1),
		GroupID:         arg.GroupID,
		ArtifactID:      arg.ArtifactID,
		Name:            arg.Name,
		Website:         arg.Website,
		GitRepositories: arg.GitRepositories,
	}
	m.artifacts[key] = a
	return a, nil
}

func (m *mockQuerier) ListTagsForVersions(ctx context.Context, versionIDs []int64) ([]db.ArtifactVersionedTag, error) {
	var result []db.ArtifactVersionedTag
	for _, id := range versionIDs {
		if tags, ok := m.tags[id]; ok {
			result = append(result, tags...)
		}
	}
	return result, nil
}

func (m *mockQuerier) GetArtifactVersion(ctx context.Context, arg db.GetArtifactVersionParams) (db.ArtifactVersion, error) {
	key := arg.GroupID + ":" + arg.ArtifactID
	artifact, ok := m.artifacts[key]
	if !ok {
		return db.ArtifactVersion{}, pgx.ErrNoRows
	}
	for _, v := range m.versions {
		if v.ArtifactID == artifact.ID && v.Version == arg.Version {
			return v, nil
		}
	}
	return db.ArtifactVersion{}, pgx.ErrNoRows
}

func (m *mockQuerier) ListArtifactVersionAssets(ctx context.Context, versionID int64) ([]db.ArtifactVersionedAsset, error) {
	return m.assets[versionID], nil
}

func (m *mockQuerier) ListArtifactVersionTags(ctx context.Context, versionID int64) ([]db.ArtifactVersionedTag, error) {
	return m.tags[versionID], nil
}

func (m *mockQuerier) CountVersionsFiltered(ctx context.Context, params repository.VersionQueryParams) (int, error) {
	// Reuse ListVersionsFiltered without limit/offset to count
	noLimit := params
	noLimit.Limit = 10000
	noLimit.Offset = 0
	versions, err := m.ListVersionsFiltered(ctx, noLimit)
	return len(versions), err
}

func (m *mockQuerier) ListVersionsFiltered(ctx context.Context, params repository.VersionQueryParams) ([]db.ArtifactVersion, error) {
	// Find the artifact
	key := params.GroupID + ":" + params.ArtifactID
	artifact, ok := m.artifacts[key]
	if !ok {
		return nil, nil
	}

	// Filter versions by artifact ID, recommended, and tags
	var filtered []db.ArtifactVersion
	for _, v := range m.versions {
		if v.ArtifactID != artifact.ID {
			continue
		}
		if params.Recommended != nil && v.Recommended != *params.Recommended {
			continue
		}
		if len(params.Tags) > 0 {
			vTags := m.tags[v.ID]
			tagMap := make(map[string]string)
			for _, t := range vTags {
				tagMap[t.TagKey] = t.TagValue
			}
			match := true
			for k, val := range params.Tags {
				if tagMap[k] != val {
					match = false
					break
				}
			}
			if !match {
				continue
			}
		}
		filtered = append(filtered, v)
	}

	// Sort by sort_order DESC (matching real DB behavior)
	slices.SortFunc(filtered, func(a, b db.ArtifactVersion) int {
		return int(b.SortOrder) - int(a.SortOrder)
	})

	// Apply offset and limit
	if int(params.Offset) >= len(filtered) {
		return nil, nil
	}
	filtered = filtered[params.Offset:]
	if int(params.Limit) < len(filtered) {
		filtered = filtered[:params.Limit]
	}
	return filtered, nil
}

func (m *mockQuerier) ListVersionsWithAssets(_ context.Context, _ repository.VersionQueryParams) (*repository.VersionsWithAssetsResult, error) {
	return &repository.VersionsWithAssetsResult{}, nil
}

func (m *mockQuerier) GetDistinctTagValues(_ context.Context, _, _ string) (map[string][]string, error) {
	return nil, nil
}

func (m *mockQuerier) GetDefaultTagValue(_ context.Context, _, _, _ string) (string, error) {
	return "", nil
}

func TestHandler(t *testing.T) {
	q := &mockQuerier{
		groups:    make(map[string]db.Group),
		artifacts: make(map[string]db.Artifact),
	}
	service := app.NewService(q)
	handler := NewHandler(service, nil, nil)

	ctx := context.Background()

	// Test RegisterGroup
	regReq := api.RegisterGroupRequestObject{
		Body: &api.RegisterGroupJSONRequestBody{
			GroupCoordinates: "org.spongepowered",
			Name:             "SpongePowered",
		},
	}
	resp, err := handler.RegisterGroup(ctx, regReq)
	if err != nil {
		t.Fatalf("RegisterGroup failed: %v", err)
	}

	if _, ok := resp.(api.RegisterGroup201JSONResponse); !ok {
		t.Errorf("Expected 201 response, got %T", resp)
	}

	// Test GetGroup
	getReq := api.GetGroupRequestObject{GroupID: "org.spongepowered"}
	getResp, err := handler.GetGroup(ctx, getReq)
	if err != nil {
		t.Fatalf("GetGroup failed: %v", err)
	}

	if r, ok := getResp.(api.GetGroup200JSONResponse); ok {
		if r.GroupCoordinates != "org.spongepowered" {
			t.Errorf("Expected org.spongepowered, got %s", r.GroupCoordinates)
		}
	} else {
		t.Errorf("Expected 200 response, got %T", getResp)
	}

	// Test GetGroups
	listResp, err := handler.GetGroups(ctx, api.GetGroupsRequestObject{})
	if err != nil {
		t.Fatalf("GetGroups failed: %v", err)
	}

	if r, ok := listResp.(api.GetGroups200JSONResponse); ok {
		if len(r.Groups) != 1 {
			t.Errorf("Expected 1 group, got %d", len(r.Groups))
		}
	} else {
		t.Errorf("Expected 200 response, got %T", listResp)
	}

	// Test RegisterArtifact
	website := "https://spongepowered.org/"
	issues := "https://github.com/SpongePowered/Sponge/issues"
	regArtReq := api.RegisterArtifactRequestObject{
		GroupID: "org.spongepowered",
		Body: &api.RegisterArtifactJSONRequestBody{
			ArtifactId:    "spongeforge",
			DisplayName:   "SpongeForge",
			GitRepository: []string{"https://github.com/SpongePowered/Sponge"},
			Website:       &website,
			Issues:        &issues,
		},
	}
	artResp, err := handler.RegisterArtifact(ctx, regArtReq)
	if err != nil {
		t.Fatalf("RegisterArtifact failed: %v", err)
	}

	if r, ok := artResp.(api.RegisterArtifact201JSONResponse); ok {
		if r.Coordinates.ArtifactId != "spongeforge" {
			t.Errorf("Expected spongeforge, got %s", r.Coordinates.ArtifactId)
		}
		if r.Coordinates.GroupId != "org.spongepowered" {
			t.Errorf("Expected org.spongepowered, got %s", r.Coordinates.GroupId)
		}
		if r.DisplayName == nil || *r.DisplayName != "SpongeForge" {
			t.Errorf("Expected SpongeForge display name")
		}
		if r.GitRepository == nil || len(*r.GitRepository) != 1 || (*r.GitRepository)[0] != "https://github.com/SpongePowered/Sponge" {
			t.Errorf("Expected git repository URL array")
		}
	} else {
		t.Errorf("Expected 201 response, got %T", artResp)
	}

	// Test RegisterArtifact with non-existent group (should return 404)
	regArtReqBadGroup := api.RegisterArtifactRequestObject{
		GroupID: "org.nonexistent",
		Body: &api.RegisterArtifactJSONRequestBody{
			ArtifactId:    "testartifact",
			DisplayName:   "Test Artifact",
			GitRepository: []string{"https://github.com/test/test"},
		},
	}
	resp404, err := handler.RegisterArtifact(ctx, regArtReqBadGroup)
	if err != nil {
		t.Fatalf("RegisterArtifact failed: %v", err)
	}
	if httpErr, ok := resp404.(*GroupNotFoundError); !ok {
		t.Errorf("Expected GroupNotFoundError (404 response) for non-existent group, got %T", resp404)
	} else if httpErr.StatusCode != 404 {
		t.Errorf("Expected status code 404, got %d", httpErr.StatusCode)
	}

	// Test RegisterArtifact with duplicate artifact (should return 409)
	// The artifact "spongeforge" was already created earlier in the test, so try to create it again
	resp409, err := handler.RegisterArtifact(ctx, regArtReq)
	if err != nil {
		t.Fatalf("RegisterArtifact failed: %v", err)
	}
	if httpErr, ok := resp409.(*ArtifactAlreadyExistsError); !ok {
		t.Errorf("Expected ArtifactAlreadyExistsError (409 response) for duplicate artifact, got %T", resp409)
	} else if httpErr.StatusCode != 409 {
		t.Errorf("Expected status code 409, got %d", httpErr.StatusCode)
	}

	// Test RegisterArtifact with missing body (should return 400)
	regArtReqMissingBody := api.RegisterArtifactRequestObject{
		GroupID: "org.spongepowered",
		Body:    nil, // Missing body
	}
	resp400, err := handler.RegisterArtifact(ctx, regArtReqMissingBody)
	if err != nil {
		t.Fatalf("RegisterArtifact failed: %v", err)
	}
	if httpErr, ok := resp400.(*BadRequestError); !ok {
		t.Errorf("Expected BadRequestError (400 response) for missing body, got %T", resp400)
	} else if httpErr.StatusCode != 400 {
		t.Errorf("Expected status code 400, got %d", httpErr.StatusCode)
	} else if httpErr.Message != "request body is required" {
		t.Errorf("Expected error message 'request body is required', got '%s'", httpErr.Message)
	}
}

func TestParseTags(t *testing.T) {
	tests := []struct {
		name    string
		input   string
		want    map[string]string
		wantErr bool
	}{
		{name: "single tag", input: "minecraft:1.12.2", want: map[string]string{"minecraft": "1.12.2"}},
		{name: "multiple tags", input: "minecraft:1.12.2,api:7.4", want: map[string]string{"minecraft": "1.12.2", "api": "7.4"}},
		{name: "with spaces around pair", input: " minecraft:1.12.2 , api:7.4 ", want: map[string]string{"minecraft": "1.12.2", "api": "7.4"}},
		{name: "with spaces around colon", input: "minecraft : 1.12.2", want: map[string]string{"minecraft": "1.12.2"}},
		{name: "trailing comma", input: "minecraft:1.12.2,", want: map[string]string{"minecraft": "1.12.2"}},
		{name: "missing value", input: "minecraft:", wantErr: true},
		{name: "missing key", input: ":1.12.2", wantErr: true},
		{name: "no colon", input: "minecraft", wantErr: true},
		{name: "empty", input: "", wantErr: true},
		{name: "only commas", input: ",,", wantErr: true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := parseTags(tt.input)
			if tt.wantErr {
				if err == nil {
					t.Errorf("expected error, got nil")
				}
				return
			}
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if len(got) != len(tt.want) {
				t.Fatalf("expected %d tags, got %d", len(tt.want), len(got))
			}
			for k, v := range tt.want {
				if got[k] != v {
					t.Errorf("tag %q: expected %q, got %q", k, v, got[k])
				}
			}
		})
	}
}

func TestGetVersions(t *testing.T) {
	q := &mockQuerier{
		groups: map[string]db.Group{
			"org.spongepowered": {MavenID: "org.spongepowered", Name: "SpongePowered"},
		},
		artifacts: map[string]db.Artifact{
			"org.spongepowered:spongeforge": {ID: 1, GroupID: "org.spongepowered", ArtifactID: "spongeforge", Name: "SpongeForge", GitRepositories: []byte("[]")},
		},
		versions: map[int64]db.ArtifactVersion{
			1: {ID: 1, ArtifactID: 1, Version: "1.12.2-2838-7.4.0", SortOrder: 100, Recommended: true},
			2: {ID: 2, ArtifactID: 1, Version: "1.12.2-2838-7.4.0-RC1", SortOrder: 99, Recommended: false},
			3: {ID: 3, ArtifactID: 1, Version: "1.16.5-36.2.5-8.1.0", SortOrder: 200, Recommended: true},
		},
		tags: map[int64][]db.ArtifactVersionedTag{
			1: {{ArtifactVersionID: 1, TagKey: "minecraft", TagValue: "1.12.2"}, {ArtifactVersionID: 1, TagKey: "api", TagValue: "7.4.0"}},
			2: {{ArtifactVersionID: 2, TagKey: "minecraft", TagValue: "1.12.2"}, {ArtifactVersionID: 2, TagKey: "api", TagValue: "7.4.0"}},
			3: {{ArtifactVersionID: 3, TagKey: "minecraft", TagValue: "1.16.5"}, {ArtifactVersionID: 3, TagKey: "api", TagValue: "8.1.0"}},
		},
	}
	service := app.NewService(q)
	handler := NewHandler(service, nil, nil)
	ctx := context.Background()

	t.Run("returns versions for existing artifact", func(t *testing.T) {
		resp, err := handler.GetVersions(ctx, api.GetVersionsRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "spongeforge",
			Params:     api.GetVersionsParams{},
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		r, ok := resp.(*orderedVersionsResponse)
		if !ok {
			t.Fatalf("expected orderedVersionsResponse, got %T", resp)
		}
		if len(r.Entries) != 3 {
			t.Errorf("expected 3 versions, got %v", r.Entries)
		}
	})

	t.Run("returns 404 for non-existent artifact", func(t *testing.T) {
		resp, err := handler.GetVersions(ctx, api.GetVersionsRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "nonexistent",
			Params:     api.GetVersionsParams{},
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if _, ok := resp.(api.GetVersions404Response); !ok {
			t.Errorf("expected 404, got %T", resp)
		}
	})

	t.Run("clamps limit above max to 25", func(t *testing.T) {
		overLimit := 50
		resp, err := handler.GetVersions(ctx, api.GetVersionsRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "spongeforge",
			Params:     api.GetVersionsParams{Limit: &overLimit},
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		ordered, ok := resp.(*orderedVersionsResponse)
		if !ok {
			t.Fatalf("expected ordered versions response, got %T", resp)
		}
		if ordered.Limit != 25 {
			t.Errorf("expected limit clamped to 25, got %d", ordered.Limit)
		}
	})

	t.Run("returns 400 for zero limit", func(t *testing.T) {
		zeroLimit := 0
		resp, err := handler.GetVersions(ctx, api.GetVersionsRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "spongeforge",
			Params:     api.GetVersionsParams{Limit: &zeroLimit},
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if _, ok := resp.(*GetVersionsBadRequestError); !ok {
			t.Errorf("expected 400, got %T", resp)
		}
	})

	t.Run("returns 400 for negative offset", func(t *testing.T) {
		negOffset := -1
		resp, err := handler.GetVersions(ctx, api.GetVersionsRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "spongeforge",
			Params:     api.GetVersionsParams{Offset: &negOffset},
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if _, ok := resp.(*GetVersionsBadRequestError); !ok {
			t.Errorf("expected 400, got %T", resp)
		}
	})

	t.Run("returns 400 for malformed tags", func(t *testing.T) {
		badTags := "invalidtag"
		resp, err := handler.GetVersions(ctx, api.GetVersionsRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "spongeforge",
			Params:     api.GetVersionsParams{Tags: &badTags},
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if _, ok := resp.(*GetVersionsBadRequestError); !ok {
			t.Errorf("expected 400, got %T", resp)
		}
	})

	t.Run("filters by recommended", func(t *testing.T) {
		rec := true
		resp, err := handler.GetVersions(ctx, api.GetVersionsRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "spongeforge",
			Params:     api.GetVersionsParams{Recommended: &rec},
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		r, ok := resp.(*orderedVersionsResponse)
		if !ok {
			t.Fatalf("expected orderedVersionsResponse, got %T", resp)
		}
		if len(r.Entries) != 2 {
			t.Errorf("expected 2 recommended versions, got %d", len(r.Entries))
		}
	})

	t.Run("filters by tags", func(t *testing.T) {
		tags := "minecraft:1.12.2"
		resp, err := handler.GetVersions(ctx, api.GetVersionsRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "spongeforge",
			Params:     api.GetVersionsParams{Tags: &tags},
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		r, ok := resp.(*orderedVersionsResponse)
		if !ok {
			t.Fatalf("expected orderedVersionsResponse, got %T", resp)
		}
		if len(r.Entries) != 2 {
			t.Errorf("expected 2 versions for minecraft:1.12.2, got %d", len(r.Entries))
		}
	})

	t.Run("respects limit", func(t *testing.T) {
		limit := 1
		resp, err := handler.GetVersions(ctx, api.GetVersionsRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "spongeforge",
			Params:     api.GetVersionsParams{Limit: &limit},
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		r, ok := resp.(*orderedVersionsResponse)
		if !ok {
			t.Fatalf("expected orderedVersionsResponse, got %T", resp)
		}
		if len(r.Entries) != 1 {
			t.Errorf("expected 1 version with limit=1, got %d", len(r.Entries))
		}
	})
}

func TestGetVersionInfo(t *testing.T) {
	classifier := "universal"
	q := &mockQuerier{
		groups: map[string]db.Group{
			"org.spongepowered": {MavenID: "org.spongepowered", Name: "SpongePowered"},
		},
		artifacts: map[string]db.Artifact{
			"org.spongepowered:spongevanilla": {ID: 1, GroupID: "org.spongepowered", ArtifactID: "spongevanilla", Name: "SpongeVanilla", GitRepositories: []byte("[]")},
		},
		versions: map[int64]db.ArtifactVersion{
			10: {ID: 10, ArtifactID: 1, Version: "1.12.2-7.4.0", SortOrder: 100, Recommended: true, CommitBody: []byte(`{"sha":"abc123","repository":"https://github.com/SpongePowered/SpongeVanilla"}`)},
			11: {ID: 11, ArtifactID: 1, Version: "1.12.2-7.4.0-RC1", SortOrder: 99, Recommended: false},
		},
		tags: map[int64][]db.ArtifactVersionedTag{
			10: {
				{ArtifactVersionID: 10, TagKey: "minecraft", TagValue: "1.12.2"},
				{ArtifactVersionID: 10, TagKey: "api", TagValue: "7.4.0"},
			},
		},
		assets: map[int64][]db.ArtifactVersionedAsset{
			10: {
				{ID: 1, ArtifactVersionID: 10, Classifier: &classifier, DownloadUrl: "https://repo.spongepowered.org/spongevanilla-1.12.2-7.4.0-universal.jar"},
			},
		},
	}
	service := app.NewService(q)
	handler := NewHandler(service, nil, nil)
	ctx := context.Background()

	t.Run("returns version detail with assets, tags, and commit", func(t *testing.T) {
		resp, err := handler.GetVersionInfo(ctx, api.GetVersionInfoRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "spongevanilla",
			VersionID:  "1.12.2-7.4.0",
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		r, ok := resp.(api.GetVersionInfo200JSONResponse)
		if !ok {
			t.Fatalf("expected 200, got %T", resp)
		}

		// Coordinates
		if r.Coordinates == nil {
			t.Fatal("expected coordinates")
		}
		if *r.Coordinates.GroupId != "org.spongepowered" {
			t.Errorf("expected groupId org.spongepowered, got %s", *r.Coordinates.GroupId)
		}
		if *r.Coordinates.ArtifactId != "spongevanilla" {
			t.Errorf("expected artifactId spongevanilla, got %s", *r.Coordinates.ArtifactId)
		}
		if *r.Coordinates.Version != "1.12.2-7.4.0" {
			t.Errorf("expected version 1.12.2-7.4.0, got %s", *r.Coordinates.Version)
		}

		// Recommended
		if r.Recommended == nil || !*r.Recommended {
			t.Error("expected recommended=true")
		}

		// Tags
		if r.Tags == nil || len(*r.Tags) != 2 {
			t.Fatalf("expected 2 tags, got %v", r.Tags)
		}
		if (*r.Tags)["minecraft"] != "1.12.2" {
			t.Errorf("expected minecraft tag 1.12.2, got %s", (*r.Tags)["minecraft"])
		}

		// Assets
		if r.Assets == nil || len(*r.Assets) != 1 {
			t.Fatalf("expected 1 asset, got %v", r.Assets)
		}
		if *(*r.Assets)[0].Classifier != "universal" {
			t.Errorf("expected classifier universal, got %s", *(*r.Assets)[0].Classifier)
		}

		// Commit
		if r.Commit == nil || r.Commit.Commits == nil || len(*r.Commit.Commits) != 1 {
			t.Fatal("expected 1 commit entry")
		}
		commitEntry := (*r.Commit.Commits)[0]
		if commitEntry.Commit == nil || *commitEntry.Commit.Sha != "abc123" {
			t.Errorf("expected commit sha abc123")
		}
		if *commitEntry.Commit.Link != "https://github.com/SpongePowered/SpongeVanilla/commit/abc123" {
			t.Errorf("expected commit link to commit URL, got %s", *commitEntry.Commit.Link)
		}
	})

	t.Run("returns 404 for non-existent version", func(t *testing.T) {
		resp, err := handler.GetVersionInfo(ctx, api.GetVersionInfoRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "spongevanilla",
			VersionID:  "999.999.999",
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if _, ok := resp.(api.GetVersionInfo404Response); !ok {
			t.Errorf("expected 404, got %T", resp)
		}
	})

	t.Run("returns 404 for non-existent artifact", func(t *testing.T) {
		resp, err := handler.GetVersionInfo(ctx, api.GetVersionInfoRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "nonexistent",
			VersionID:  "1.0.0",
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if _, ok := resp.(api.GetVersionInfo404Response); !ok {
			t.Errorf("expected 404, got %T", resp)
		}
	})

	t.Run("returns changelog commits when changelog exists", func(t *testing.T) {
		// Add a version with full changelog in commit_body
		q.versions[12] = db.ArtifactVersion{
			ID: 12, ArtifactID: 1, Version: "1.12.2-7.5.0", SortOrder: 101, Recommended: false,
			CommitBody: []byte(`{
				"sha":"def456",
				"repository":"https://github.com/SpongePowered/SpongeVanilla",
				"message":"feat: add new API",
				"changelog":{
					"previousVersion":"1.12.2-7.4.0",
					"commits":[
						{"sha":"def456","message":"feat: add new API","author":{"name":"Alice","email":"alice@example.com"},"commitDate":"2026-03-28"},
						{"sha":"ccc333","message":"fix: correct event handling","author":{"name":"Bob","email":"bob@example.com"},"commitDate":"2026-03-27"},
						{"sha":"bbb222","message":"refactor: clean up mixins","commitDate":"2026-03-26"}
					]
				}
			}`),
		}
		q.tags[12] = []db.ArtifactVersionedTag{}
		q.assets[12] = []db.ArtifactVersionedAsset{}

		resp, err := handler.GetVersionInfo(ctx, api.GetVersionInfoRequestObject{
			GroupID: "org.spongepowered", ArtifactID: "spongevanilla", VersionID: "1.12.2-7.5.0",
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		r, ok := resp.(api.GetVersionInfo200JSONResponse)
		if !ok {
			t.Fatalf("expected 200, got %T", resp)
		}
		if r.Commit == nil || r.Commit.Commits == nil {
			t.Fatal("expected commits")
		}
		commits := *r.Commit.Commits
		if len(commits) != 3 {
			t.Fatalf("expected 3 changelog commits, got %d", len(commits))
		}
		if *commits[0].Commit.Sha != "def456" {
			t.Errorf("expected first commit sha def456, got %s", *commits[0].Commit.Sha)
		}
		if *commits[1].Commit.Sha != "ccc333" {
			t.Errorf("expected second commit sha ccc333, got %s", *commits[1].Commit.Sha)
		}
		if *commits[2].Commit.Sha != "bbb222" {
			t.Errorf("expected third commit sha bbb222, got %s", *commits[2].Commit.Sha)
		}
		if *commits[0].Commit.Message != "feat: add new API" {
			t.Errorf("expected message 'feat: add new API', got %s", *commits[0].Commit.Message)
		}
		// No submodule commits on any entry
		for i, c := range commits {
			if c.SubmoduleCommits != nil {
				t.Errorf("commit %d: expected no submodule commits", i)
			}
		}
	})

	t.Run("includes submodule changelog commits", func(t *testing.T) {
		q.versions[13] = db.ArtifactVersion{
			ID: 13, ArtifactID: 1, Version: "1.12.2-7.6.0", SortOrder: 102, Recommended: false,
			CommitBody: []byte(`{
				"sha":"eee555",
				"repository":"https://github.com/SpongePowered/SpongeVanilla",
				"message":"feat: bump API",
				"changelog":{
					"previousVersion":"1.12.2-7.5.0",
					"commits":[
						{"sha":"eee555","message":"feat: bump API","commitDate":"2026-03-29"}
					],
					"submoduleChangelogs":{
						"https://github.com/SpongePowered/SpongeAPI.git":{
							"previousVersion":"",
							"commits":[
								{"sha":"sub111","message":"feat: new event API","commitDate":"2026-03-28"},
								{"sha":"sub222","message":"fix: event bus NPE","commitDate":"2026-03-27"}
							]
						}
					}
				}
			}`),
		}
		q.tags[13] = []db.ArtifactVersionedTag{}
		q.assets[13] = []db.ArtifactVersionedAsset{}

		resp, err := handler.GetVersionInfo(ctx, api.GetVersionInfoRequestObject{
			GroupID: "org.spongepowered", ArtifactID: "spongevanilla", VersionID: "1.12.2-7.6.0",
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		r, ok := resp.(api.GetVersionInfo200JSONResponse)
		if !ok {
			t.Fatalf("expected 200, got %T", resp)
		}
		commits := *r.Commit.Commits
		if len(commits) != 1 {
			t.Fatalf("expected 1 main commit, got %d", len(commits))
		}
		if commits[0].SubmoduleCommits == nil {
			t.Fatal("expected submodule commits on first entry")
		}
		subs := *commits[0].SubmoduleCommits
		if len(subs) != 2 {
			t.Fatalf("expected 2 submodule commits, got %d", len(subs))
		}
		if *subs[0].Sha != "sub111" {
			t.Errorf("expected sub commit sha sub111, got %s", *subs[0].Sha)
		}
		if *subs[0].Link != "https://github.com/SpongePowered/SpongeAPI/commit/sub111" {
			t.Errorf("expected sub commit link to SpongeAPI commit URL, got %s", *subs[0].Link)
		}
	})

	t.Run("handles version with no commit body", func(t *testing.T) {
		resp, err := handler.GetVersionInfo(ctx, api.GetVersionInfoRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "spongevanilla",
			VersionID:  "1.12.2-7.4.0-RC1",
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		r, ok := resp.(api.GetVersionInfo200JSONResponse)
		if !ok {
			t.Fatalf("expected 200, got %T", resp)
		}
		if r.Commit != nil {
			t.Error("expected nil commit for version without commit body")
		}
		if r.Recommended == nil || *r.Recommended {
			t.Error("expected recommended=false")
		}
	})
}

func (m *mockQuerier) GetArtifactVersionSchema(ctx context.Context, arg db.GetArtifactVersionSchemaParams) ([]byte, error) {
	key := arg.GroupID + ":" + arg.ArtifactID
	a, ok := m.artifacts[key]
	if !ok {
		return nil, pgx.ErrNoRows
	}
	return a.VersionSchema, nil
}

func (m *mockQuerier) UpdateArtifactVersionSchema(ctx context.Context, arg db.UpdateArtifactVersionSchemaParams) error {
	key := arg.GroupID + ":" + arg.ArtifactID
	a, ok := m.artifacts[key]
	if !ok {
		return pgx.ErrNoRows
	}
	a.VersionSchema = arg.VersionSchema
	m.artifacts[key] = a
	return nil
}

func (m *mockQuerier) UpdateArtifactFields(ctx context.Context, arg db.UpdateArtifactFieldsParams) (db.Artifact, error) { //nolint:gocritic // interface implementation
	key := arg.GroupID + ":" + arg.ArtifactID
	a, ok := m.artifacts[key]
	if !ok {
		return db.Artifact{}, pgx.ErrNoRows
	}
	if arg.Name != nil {
		a.Name = *arg.Name
	}
	if arg.Website != nil {
		a.Website = arg.Website
	}
	if arg.Issues != nil {
		a.Issues = arg.Issues
	}
	if arg.GitRepositories != nil {
		a.GitRepositories = arg.GitRepositories
	}
	m.artifacts[key] = a
	return a, nil
}

func TestPutArtifactSchema(t *testing.T) {
	schemaJSON, _ := json.Marshal(map[string]any{
		"use_mojang_manifest": true,
		"variants": []map[string]any{
			{"name": "current", "pattern": `^(?P<mc>\d+)-(?P<api>\d+)$`, "segments": []map[string]any{
				{"name": "mc", "parse_as": "minecraft", "tag_key": "minecraft"},
				{"name": "api", "parse_as": "integer", "tag_key": "api"},
			}},
		},
	})

	q := &mockQuerier{
		groups: map[string]db.Group{
			"org.spongepowered": {MavenID: "org.spongepowered", Name: "SpongePowered"},
		},
		artifacts: map[string]db.Artifact{
			"org.spongepowered:spongevanilla": {ID: 1, GroupID: "org.spongepowered", ArtifactID: "spongevanilla", Name: "SpongeVanilla", GitRepositories: []byte("[]"), VersionSchema: schemaJSON},
		},
	}
	service := app.NewService(q)
	handler := NewHandler(service, nil, nil)
	ctx := context.Background()

	useMojang := true
	mcStr := "minecraft"
	apiStr := "api"

	t.Run("updates schema and returns 200", func(t *testing.T) {
		resp, err := handler.PutArtifactSchema(ctx, api.PutArtifactSchemaRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "spongevanilla",
			Body: &api.VersionSchema{
				UseMojangManifest: &useMojang,
				Variants: []struct {
					Name     string `json:"name"`
					Pattern  string `json:"pattern"`
					Segments []struct {
						Name    string                                   `json:"name"`
						ParseAs api.VersionSchemaVariantsSegmentsParseAs `json:"parse_as"`
						TagKey  *string                                  `json:"tag_key,omitempty"`
					} `json:"segments"`
				}{
					{
						Name: "current", Pattern: `^(?P<mc>\d+)-(?P<api>\d+)$`,
						Segments: []struct {
							Name    string                                   `json:"name"`
							ParseAs api.VersionSchemaVariantsSegmentsParseAs `json:"parse_as"`
							TagKey  *string                                  `json:"tag_key,omitempty"`
						}{
							{Name: "mc", ParseAs: api.Minecraft, TagKey: &mcStr},
							{Name: "api", ParseAs: api.Integer, TagKey: &apiStr},
						},
					},
				},
			},
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if _, ok := resp.(api.PutArtifactSchema200JSONResponse); !ok {
			t.Errorf("expected 200, got %T", resp)
		}
	})

	t.Run("returns 404 for nonexistent artifact", func(t *testing.T) {
		resp, err := handler.PutArtifactSchema(ctx, api.PutArtifactSchemaRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "nonexistent",
			Body: &api.VersionSchema{
				Variants: []struct {
					Name     string `json:"name"`
					Pattern  string `json:"pattern"`
					Segments []struct {
						Name    string                                   `json:"name"`
						ParseAs api.VersionSchemaVariantsSegmentsParseAs `json:"parse_as"`
						TagKey  *string                                  `json:"tag_key,omitempty"`
					} `json:"segments"`
				}{
					{Name: "test", Pattern: `^(?P<v>\d+)$`, Segments: nil},
				},
			},
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if _, ok := resp.(api.PutArtifactSchema404Response); !ok {
			t.Errorf("expected 404, got %T", resp)
		}
	})

	t.Run("returns 400 for invalid regex", func(t *testing.T) {
		resp, err := handler.PutArtifactSchema(ctx, api.PutArtifactSchemaRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "spongevanilla",
			Body: &api.VersionSchema{
				Variants: []struct {
					Name     string `json:"name"`
					Pattern  string `json:"pattern"`
					Segments []struct {
						Name    string                                   `json:"name"`
						ParseAs api.VersionSchemaVariantsSegmentsParseAs `json:"parse_as"`
						TagKey  *string                                  `json:"tag_key,omitempty"`
					} `json:"segments"`
				}{
					{Name: "bad", Pattern: `^[invalid`, Segments: nil},
				},
			},
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if _, ok := resp.(api.PutArtifactSchema400Response); !ok {
			t.Errorf("expected 400, got %T", resp)
		}
	})
}

func TestGetArtifactSchema(t *testing.T) {
	schemaJSON, _ := json.Marshal(map[string]any{
		"use_mojang_manifest": false,
		"variants":            []map[string]any{{"name": "test", "pattern": `^(?P<v>\d+)$`, "segments": []map[string]any{{"name": "v", "parse_as": "integer"}}}},
	})

	q := &mockQuerier{
		groups: map[string]db.Group{},
		artifacts: map[string]db.Artifact{
			"org.spongepowered:spongevanilla": {ID: 1, GroupID: "org.spongepowered", ArtifactID: "spongevanilla", Name: "SV", GitRepositories: []byte("[]"), VersionSchema: schemaJSON},
			"org.spongepowered:noschema":      {ID: 2, GroupID: "org.spongepowered", ArtifactID: "noschema", Name: "NS", GitRepositories: []byte("[]")},
		},
	}
	service := app.NewService(q)
	handler := NewHandler(service, nil, nil)
	ctx := context.Background()

	t.Run("returns schema", func(t *testing.T) {
		resp, err := handler.GetArtifactSchema(ctx, api.GetArtifactSchemaRequestObject{
			GroupID: "org.spongepowered", ArtifactID: "spongevanilla",
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		r, ok := resp.(api.GetArtifactSchema200JSONResponse)
		if !ok {
			t.Fatalf("expected 200, got %T", resp)
		}
		if len(r.Variants) != 1 {
			t.Errorf("expected 1 variant, got %d", len(r.Variants))
		}
	})

	t.Run("returns 404 for no schema", func(t *testing.T) {
		resp, err := handler.GetArtifactSchema(ctx, api.GetArtifactSchemaRequestObject{
			GroupID: "org.spongepowered", ArtifactID: "noschema",
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if _, ok := resp.(api.GetArtifactSchema404Response); !ok {
			t.Errorf("expected 404, got %T", resp)
		}
	})

	t.Run("returns 404 for nonexistent artifact", func(t *testing.T) {
		resp, err := handler.GetArtifactSchema(ctx, api.GetArtifactSchemaRequestObject{
			GroupID: "org.spongepowered", ArtifactID: "nonexistent",
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if _, ok := resp.(api.GetArtifactSchema404Response); !ok {
			t.Errorf("expected 404, got %T", resp)
		}
	})
}

func TestUpdateArtifact(t *testing.T) {
	q := &mockQuerier{
		groups: map[string]db.Group{},
		artifacts: map[string]db.Artifact{
			"org.spongepowered:spongevanilla": {ID: 1, GroupID: "org.spongepowered", ArtifactID: "spongevanilla", Name: "SpongeVanilla", GitRepositories: []byte(`["https://github.com/SpongePowered/SpongeVanilla"]`)},
		},
	}
	service := app.NewService(q)
	handler := NewHandler(service, nil, nil)
	ctx := context.Background()

	t.Run("updates display name", func(t *testing.T) {
		newName := "SpongeVanilla Updated"
		resp, err := handler.UpdateArtifact(ctx, api.UpdateArtifactRequestObject{
			GroupID: "org.spongepowered", ArtifactID: "spongevanilla",
			Body: &api.UpdateArtifactJSONRequestBody{DisplayName: &newName},
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		r, ok := resp.(api.UpdateArtifact200JSONResponse)
		if !ok {
			t.Fatalf("expected 200, got %T", resp)
		}
		if r.DisplayName == nil || *r.DisplayName != "SpongeVanilla Updated" {
			t.Errorf("expected updated display name")
		}
	})

	t.Run("updates git repositories", func(t *testing.T) {
		newRepos := []string{"https://github.com/SpongePowered/SpongeCommon"}
		resp, err := handler.UpdateArtifact(ctx, api.UpdateArtifactRequestObject{
			GroupID: "org.spongepowered", ArtifactID: "spongevanilla",
			Body: &api.UpdateArtifactJSONRequestBody{GitRepository: &newRepos},
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		r, ok := resp.(api.UpdateArtifact200JSONResponse)
		if !ok {
			t.Fatalf("expected 200, got %T", resp)
		}
		if r.GitRepository == nil || len(*r.GitRepository) != 1 || (*r.GitRepository)[0] != "https://github.com/SpongePowered/SpongeCommon" {
			t.Errorf("expected updated git repos")
		}
	})

	t.Run("returns 404 for nonexistent artifact", func(t *testing.T) {
		name := "test"
		resp, err := handler.UpdateArtifact(ctx, api.UpdateArtifactRequestObject{
			GroupID: "org.spongepowered", ArtifactID: "nonexistent",
			Body: &api.UpdateArtifactJSONRequestBody{DisplayName: &name},
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if _, ok := resp.(api.UpdateArtifact404Response); !ok {
			t.Errorf("expected 404, got %T", resp)
		}
	})
}

func TestGetVersionsHTTPOrdering(t *testing.T) {
	// Versions with descending sort_order — the JSON output must preserve this order.
	q := &mockQuerier{
		groups: map[string]db.Group{
			"org.spongepowered": {MavenID: "org.spongepowered", Name: "SpongePowered"},
		},
		artifacts: map[string]db.Artifact{
			"org.spongepowered:spongevanilla": {ID: 1, GroupID: "org.spongepowered", ArtifactID: "spongevanilla", Name: "SpongeVanilla", GitRepositories: []byte("[]")},
		},
		versions: map[int64]db.ArtifactVersion{
			1: {ID: 1, ArtifactID: 1, Version: "1.12.2-7.4.7", SortOrder: 1005, Recommended: true},
			2: {ID: 2, ArtifactID: 1, Version: "1.12.2-7.4.7-RC424", SortOrder: 1004, Recommended: false},
			3: {ID: 3, ArtifactID: 1, Version: "1.12.2-7.4.7-RC423", SortOrder: 1003, Recommended: false},
			4: {ID: 4, ArtifactID: 1, Version: "1.12.2-7.4.6", SortOrder: 1002, Recommended: true},
			5: {ID: 5, ArtifactID: 1, Version: "1.12.2-7.4.5", SortOrder: 1001, Recommended: true},
		},
		tags: map[int64][]db.ArtifactVersionedTag{
			1: {{ArtifactVersionID: 1, TagKey: "minecraft", TagValue: "1.12.2"}},
			2: {{ArtifactVersionID: 2, TagKey: "minecraft", TagValue: "1.12.2"}},
			3: {{ArtifactVersionID: 3, TagKey: "minecraft", TagValue: "1.12.2"}},
			4: {{ArtifactVersionID: 4, TagKey: "minecraft", TagValue: "1.12.2"}},
			5: {{ArtifactVersionID: 5, TagKey: "minecraft", TagValue: "1.12.2"}},
		},
	}
	service := app.NewService(q)
	handler := NewHandler(service, nil, nil)

	// Wire through the full HTTP stack
	apiHandler := api.NewStrictHandler(handler, nil)
	mux := http.NewServeMux()
	httpHandler := api.HandlerFromMux(apiHandler, mux)

	srv := httptest.NewServer(httpHandler)
	defer srv.Close()

	resp, err := http.Get(srv.URL + "/groups/org.spongepowered/artifacts/spongevanilla/versions?limit=5")
	if err != nil {
		t.Fatalf("HTTP request failed: %v", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode != 200 {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}

	// Read raw body to verify JSON key order
	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		t.Fatalf("reading body: %v", err)
	}
	body := string(bodyBytes)

	// Verify the versions appear in descending sort_order in the raw JSON
	expectedOrder := []string{
		"1.12.2-7.4.7",
		"1.12.2-7.4.7-RC424",
		"1.12.2-7.4.7-RC423",
		"1.12.2-7.4.6",
		"1.12.2-7.4.5",
	}

	lastIdx := -1
	for _, version := range expectedOrder {
		idx := strings.Index(body, `"`+version+`"`)
		if idx == -1 {
			t.Errorf("version %q not found in response body", version)
			continue
		}
		if idx <= lastIdx {
			t.Errorf("version %q (pos %d) appears before previous version (pos %d) — order not preserved", version, idx, lastIdx)
		}
		lastIdx = idx
	}

	// Also verify pagination metadata exists
	if !strings.Contains(body, `"offset":0`) {
		t.Error("missing offset in response")
	}
	if !strings.Contains(body, `"limit":5`) {
		t.Error("missing limit in response")
	}
	if !strings.Contains(body, `"size":5`) {
		t.Error("missing size in response")
	}

	t.Logf("Response body:\n%s", body)
}

// --- Mock types for Temporal ScheduleClient / ScheduleHandle ---

type mockScheduleHandle struct {
	triggerErr error
}

func (m *mockScheduleHandle) GetID() string                  { return "" }
func (m *mockScheduleHandle) Delete(_ context.Context) error { return nil }
func (m *mockScheduleHandle) Backfill(_ context.Context, _ client.ScheduleBackfillOptions) error {
	return nil
}
func (m *mockScheduleHandle) Update(_ context.Context, _ client.ScheduleUpdateOptions) error {
	return nil
}
func (m *mockScheduleHandle) Describe(_ context.Context) (*client.ScheduleDescription, error) {
	return nil, nil
}
func (m *mockScheduleHandle) Pause(_ context.Context, _ client.SchedulePauseOptions) error {
	return nil
}
func (m *mockScheduleHandle) Unpause(_ context.Context, _ client.ScheduleUnpauseOptions) error {
	return nil
}
func (m *mockScheduleHandle) Trigger(_ context.Context, _ client.ScheduleTriggerOptions) error {
	return m.triggerErr
}

type mockScheduleClient struct {
	handle *mockScheduleHandle
}

func (m *mockScheduleClient) Create(_ context.Context, _ client.ScheduleOptions) (client.ScheduleHandle, error) { //nolint:gocritic // test mock
	return m.handle, nil
}

func (m *mockScheduleClient) List(_ context.Context, _ client.ScheduleListOptions) (client.ScheduleListIterator, error) {
	return nil, nil
}

func (m *mockScheduleClient) GetHandle(_ context.Context, _ string) client.ScheduleHandle {
	return m.handle
}

func TestVersionSyncScheduleID(t *testing.T) {
	tests := []struct {
		groupID    string
		artifactID string
		want       string
	}{
		{"org.spongepowered", "spongeforge", "version-sync-org.spongepowered-spongeforge"},
		{"com.example", "my-lib", "version-sync-com.example-my-lib"},
		{"a", "b", "version-sync-a-b"},
	}
	for _, tt := range tests {
		t.Run(tt.groupID+"/"+tt.artifactID, func(t *testing.T) {
			got := VersionSyncScheduleID(tt.groupID, tt.artifactID)
			if got != tt.want {
				t.Errorf("VersionSyncScheduleID(%q, %q) = %q, want %q", tt.groupID, tt.artifactID, got, tt.want)
			}
		})
	}
}

func TestTriggerSync(t *testing.T) {
	q := &mockQuerier{
		groups:    make(map[string]db.Group),
		artifacts: make(map[string]db.Artifact),
	}
	service := app.NewService(q)
	ctx := context.Background()

	t.Run("happy path", func(t *testing.T) {
		sc := &mockScheduleClient{handle: &mockScheduleHandle{}}
		h := NewHandler(service, nil, sc)

		resp, err := h.TriggerSync(ctx, api.TriggerSyncRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "spongeforge",
		})
		if err != nil {
			t.Fatalf("TriggerSync failed: %v", err)
		}
		if _, ok := resp.(api.TriggerSync200JSONResponse); !ok {
			t.Errorf("expected 200 response, got %T", resp)
		}
	})

	t.Run("schedule not found", func(t *testing.T) {
		sc := &mockScheduleClient{handle: &mockScheduleHandle{
			triggerErr: serviceerror.NewNotFound("schedule not found"),
		}}
		h := NewHandler(service, nil, sc)

		resp, err := h.TriggerSync(ctx, api.TriggerSyncRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "nonexistent",
		})
		if err != nil {
			t.Fatalf("TriggerSync failed: %v", err)
		}
		if _, ok := resp.(api.TriggerSync404Response); !ok {
			t.Errorf("expected 404 response, got %T", resp)
		}
	})

	t.Run("temporal unavailable", func(t *testing.T) {
		sc := &mockScheduleClient{handle: &mockScheduleHandle{
			triggerErr: fmt.Errorf("connection refused"),
		}}
		h := NewHandler(service, nil, sc)

		resp, err := h.TriggerSync(ctx, api.TriggerSyncRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "spongeforge",
		})
		if err != nil {
			t.Fatalf("TriggerSync failed: %v", err)
		}
		if _, ok := resp.(api.TriggerSync500Response); !ok {
			t.Errorf("expected 500 response, got %T", resp)
		}
	})

	t.Run("nil schedule client", func(t *testing.T) {
		h := NewHandler(service, nil, nil)

		resp, err := h.TriggerSync(ctx, api.TriggerSyncRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "spongeforge",
		})
		if err != nil {
			t.Fatalf("TriggerSync failed: %v", err)
		}
		if _, ok := resp.(api.TriggerSync500Response); !ok {
			t.Errorf("expected 500 response, got %T", resp)
		}
	})
}

func TestTriggerSyncHTTP(t *testing.T) {
	q := &mockQuerier{
		groups:    make(map[string]db.Group),
		artifacts: make(map[string]db.Artifact),
	}
	service := app.NewService(q)
	sc := &mockScheduleClient{handle: &mockScheduleHandle{}}
	h := NewHandler(service, nil, sc)

	token := "test-secret-token"
	middlewares := []api.StrictMiddlewareFunc{
		AdminAuthMiddleware(NewTokenSet([]string{token}, nil)),
	}
	apiHandler := api.NewStrictHandler(h, middlewares)
	mux := api.HandlerFromMux(apiHandler, http.NewServeMux())
	srv := httptest.NewServer(mux)
	defer srv.Close()

	t.Run("POST with valid token returns 200", func(t *testing.T) {
		req, _ := http.NewRequest("POST", srv.URL+"/groups/org.spongepowered/artifacts/spongeforge/sync", http.NoBody)
		req.Header.Set("Authorization", "Bearer "+token)
		resp, err := http.DefaultClient.Do(req)
		if err != nil {
			t.Fatalf("request failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != 200 {
			body, _ := io.ReadAll(resp.Body)
			t.Fatalf("expected 200, got %d: %s", resp.StatusCode, body)
		}
	})

	t.Run("POST without token returns 401", func(t *testing.T) {
		req, _ := http.NewRequest("POST", srv.URL+"/groups/org.spongepowered/artifacts/spongeforge/sync", http.NoBody)
		resp, err := http.DefaultClient.Do(req)
		if err != nil {
			t.Fatalf("request failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != 401 {
			t.Errorf("expected 401, got %d", resp.StatusCode)
		}
	})

	t.Run("POST with wrong token returns 401", func(t *testing.T) {
		req, _ := http.NewRequest("POST", srv.URL+"/groups/org.spongepowered/artifacts/spongeforge/sync", http.NoBody)
		req.Header.Set("Authorization", "Bearer wrong-token")
		resp, err := http.DefaultClient.Do(req)
		if err != nil {
			t.Fatalf("request failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != 401 {
			t.Errorf("expected 401, got %d", resp.StatusCode)
		}
	})

	t.Run("GET returns 405", func(t *testing.T) {
		req, _ := http.NewRequest("GET", srv.URL+"/groups/org.spongepowered/artifacts/spongeforge/sync", http.NoBody)
		req.Header.Set("Authorization", "Bearer "+token)
		resp, err := http.DefaultClient.Do(req)
		if err != nil {
			t.Fatalf("request failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != 405 {
			t.Errorf("expected 405, got %d", resp.StatusCode)
		}
	})
}
