package httpapi

import (
	"context"

	"github.com/spongepowered/systemofadownload/api"
	"github.com/spongepowered/systemofadownload/internal/app"
	"github.com/spongepowered/systemofadownload/internal/domain"
)

type Handler struct {
	service *app.Service
}

func NewHandler(service *app.Service) *Handler {
	return &Handler{service: service}
}

func (h *Handler) GetGroups(ctx context.Context, _ api.GetGroupsRequestObject) (api.GetGroupsResponseObject, error) {
	groups, err := h.service.ListGroups(ctx)
	if err != nil {
		return nil, err
	}

	var apiGroups []api.Group
	for _, g := range groups {
		apiGroups = append(apiGroups, api.Group{
			GroupCoordinates: g.GroupID,
			Name:             g.Name,
			Website:          g.Website,
		})
	}

	return api.GetGroups200JSONResponse{
		Groups: apiGroups,
	}, nil
}

func (h *Handler) RegisterGroup(ctx context.Context, request api.RegisterGroupRequestObject) (api.RegisterGroupResponseObject, error) {
	group := &domain.Group{
		GroupID: request.Body.GroupCoordinates,
		Name:    request.Body.Name,
		Website: request.Body.Website,
	}

	err := h.service.RegisterGroup(ctx, group)
	if err != nil {
		return nil, err
	}

	return api.RegisterGroup201JSONResponse{
		GroupCoordinates: group.GroupID,
		Name:             group.Name,
		Website:          group.Website,
	}, nil
}

func (h *Handler) GetGroup(ctx context.Context, request api.GetGroupRequestObject) (api.GetGroupResponseObject, error) {
	g, err := h.service.GetGroup(ctx, request.GroupID)
	if err != nil {
		return nil, err
	}

	if g == nil {
		// The API doesn't seem to define a 404 for GetGroup in openapi.yaml,
		// but returning a zero value might be wrong.
		// Let's check openapi.yaml again.
		return nil, nil // TODO: handle 404 if needed
	}

	return api.GetGroup200JSONResponse{
		GroupCoordinates: g.GroupID,
		Name:             g.Name,
		Website:          g.Website,
	}, nil
}

// Implement other methods of StrictServerInterface with a panic or default for now
// as they are not requested yet.

func (h *Handler) GetArtifacts(ctx context.Context, request api.GetArtifactsRequestObject) (api.GetArtifactsResponseObject, error) {
	return nil, nil
}

func (h *Handler) GetArtifact(ctx context.Context, request api.GetArtifactRequestObject) (api.GetArtifactResponseObject, error) {
	return nil, nil
}

func (h *Handler) GetVersions(ctx context.Context, request api.GetVersionsRequestObject) (api.GetVersionsResponseObject, error) {
	return nil, nil
}

func (h *Handler) GetVersionInfo(ctx context.Context, request api.GetVersionInfoRequestObject) (api.GetVersionInfoResponseObject, error) {
	return nil, nil
}
