package httpapi

import (
	"context"
	"errors"
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

func TestHandler(t *testing.T) {
	q := &mockQuerier{
		groups:    make(map[string]db.Group),
		artifacts: make(map[string]db.Artifact),
	}
	service := app.NewService(q)
	handler := NewHandler(service)

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

	// Test RegisterArtifact with non-existent group (should return error)
	regArtReqBadGroup := api.RegisterArtifactRequestObject{
		GroupID: "org.nonexistent",
		Body: &api.RegisterArtifactJSONRequestBody{
			ArtifactId:    "testartifact",
			DisplayName:   "Test Artifact",
			GitRepository: []string{"https://github.com/test/test"},
		},
	}
	_, err = handler.RegisterArtifact(ctx, regArtReqBadGroup)
	if err == nil {
		t.Errorf("Expected error when registering artifact for non-existent group")
	}
	if !errors.Is(err, app.ErrGroupNotFound) {
		t.Errorf("Expected ErrGroupNotFound, got %v", err)
	}
}
