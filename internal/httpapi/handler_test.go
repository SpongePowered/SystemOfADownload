package httpapi

import (
	"context"
	"testing"

	"github.com/jackc/pgx/v5"
	"github.com/spongepowered/systemofadownload/api"
	"github.com/spongepowered/systemofadownload/internal/app"
	"github.com/spongepowered/systemofadownload/internal/db"
)

type mockQuerier struct {
	db.Querier
	groups map[string]db.Group
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

func TestHandler(t *testing.T) {
	q := &mockQuerier{groups: make(map[string]db.Group)}
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
}
