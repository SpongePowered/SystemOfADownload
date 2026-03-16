package httpapi

import (
	"context"
	"testing"

	"github.com/jackc/pgx/v5"
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
	g := db.Group{
		MavenID: arg.MavenID,
		Name:    arg.Name,
		Website: arg.Website,
	}
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

func (m *mockQuerier) CreateArtifact(ctx context.Context, arg db.CreateArtifactParams) (db.Artifact, error) {
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

func TestHandler(t *testing.T) {
	q := &mockQuerier{
		groups:    make(map[string]db.Group),
		artifacts: make(map[string]db.Artifact),
	}
	service := app.NewService(q)
	handler := NewHandler(service, nil)

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
	handler := NewHandler(service, nil)
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
		r, ok := resp.(api.GetVersions200JSONResponse)
		if !ok {
			t.Fatalf("expected 200, got %T", resp)
		}
		if r.Artifacts == nil || len(*r.Artifacts) != 3 {
			t.Errorf("expected 3 versions, got %v", r.Artifacts)
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

	t.Run("returns 400 for invalid limit", func(t *testing.T) {
		badLimit := 50
		resp, err := handler.GetVersions(ctx, api.GetVersionsRequestObject{
			GroupID:    "org.spongepowered",
			ArtifactID: "spongeforge",
			Params:     api.GetVersionsParams{Limit: &badLimit},
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if _, ok := resp.(*GetVersionsBadRequestError); !ok {
			t.Errorf("expected 400, got %T", resp)
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
		r, ok := resp.(api.GetVersions200JSONResponse)
		if !ok {
			t.Fatalf("expected 200, got %T", resp)
		}
		if r.Artifacts == nil || len(*r.Artifacts) != 2 {
			t.Errorf("expected 2 recommended versions, got %d", len(*r.Artifacts))
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
		r, ok := resp.(api.GetVersions200JSONResponse)
		if !ok {
			t.Fatalf("expected 200, got %T", resp)
		}
		if r.Artifacts == nil || len(*r.Artifacts) != 2 {
			t.Errorf("expected 2 versions for minecraft:1.12.2, got %d", len(*r.Artifacts))
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
		r, ok := resp.(api.GetVersions200JSONResponse)
		if !ok {
			t.Fatalf("expected 200, got %T", resp)
		}
		if r.Artifacts == nil || len(*r.Artifacts) != 1 {
			t.Errorf("expected 1 version with limit=1, got %d", len(*r.Artifacts))
		}
	})
}
