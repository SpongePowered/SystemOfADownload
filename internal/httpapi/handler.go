package httpapi

import (
	"context"
	"errors"
	"fmt"
	"log/slog"

	"go.temporal.io/sdk/client"

	"github.com/spongepowered/systemofadownload/api"
	"github.com/spongepowered/systemofadownload/internal/app"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/workflow"
)

// WorkflowStarter is a narrow interface for starting Temporal workflows.
type WorkflowStarter interface {
	ExecuteWorkflow(ctx context.Context, options client.StartWorkflowOptions, workflow interface{}, args ...interface{}) (client.WorkflowRun, error)
}

type Handler struct {
	service   *app.Service
	workflows WorkflowStarter
}

func NewHandler(service *app.Service, workflows WorkflowStarter) *Handler {
	return &Handler{service: service, workflows: workflows}
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
		if errors.Is(err, app.ErrGroupNotFound) {
			return api.GetGroup404Response{}, nil
		}
		return nil, err
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
	artifactIDs, err := h.service.ListArtifacts(ctx, request.GroupID)
	if err != nil {
		return nil, err
	}

	if artifactIDs == nil {
		artifactIDs = []string{}
	}

	return api.GetArtifacts200JSONResponse{
		Type:        "group",
		ArtifactIds: artifactIDs,
	}, nil
}

func (h *Handler) RegisterArtifact(ctx context.Context, request api.RegisterArtifactRequestObject) (api.RegisterArtifactResponseObject, error) {
	if request.Body == nil {
		return NewBadRequestError("request body is required"), nil
	}

	artifact := &domain.Artifact{
		GroupID:         request.GroupID,
		ArtifactID:      request.Body.ArtifactId,
		DisplayName:     request.Body.DisplayName,
		GitRepositories: request.Body.GitRepository,
		Website:         request.Body.Website,
		Issues:          request.Body.Issues,
	}

	err := h.service.RegisterArtifact(ctx, artifact)
	if err != nil {
		// Return appropriate HTTP error types based on error type
		if errors.Is(err, app.ErrGroupNotFound) {
			return NewGroupNotFoundError(), nil
		}
		if errors.Is(err, app.ErrArtifactAlreadyExists) {
			return NewArtifactAlreadyExistsError(), nil
		}
		return nil, err
	}

	// Fire-and-forget: trigger version sync workflow
	if h.workflows != nil {
		workflowID := fmt.Sprintf("version-sync-%s-%s", request.GroupID, request.Body.ArtifactId)
		_, err := h.workflows.ExecuteWorkflow(ctx, client.StartWorkflowOptions{
			ID:        workflowID,
			TaskQueue: workflow.VersionSyncTaskQueue,
		}, workflow.VersionSyncWorkflow, workflow.VersionSyncInput{
			GroupID:    request.GroupID,
			ArtifactID: request.Body.ArtifactId,
		})
		if err != nil {
			slog.ErrorContext(ctx, "failed to start version sync workflow",
				"groupID", request.GroupID,
				"artifactID", request.Body.ArtifactId,
				"error", err)
		}
	}

	response := api.RegisterArtifact201JSONResponse{
		Type: "latest",
		Coordinates: struct {
			ArtifactId string `json:"artifactId"`
			GroupId    string `json:"groupId"`
		}{
			ArtifactId: request.Body.ArtifactId,
			GroupId:    request.GroupID,
		},
		DisplayName:   &request.Body.DisplayName,
		GitRepository: &request.Body.GitRepository,
		Website:       request.Body.Website,
		Issues:        request.Body.Issues,
		Tags:          map[string][]string{},
	}

	return response, nil
}

func (h *Handler) GetArtifact(ctx context.Context, request api.GetArtifactRequestObject) (api.GetArtifactResponseObject, error) {
	artifact, tags, err := h.service.GetArtifact(ctx, request.GroupID, request.ArtifactID)
	if err != nil {
		if errors.Is(err, app.ErrArtifactNotFound) {
			return api.GetArtifact404Response{}, nil
		}
		return nil, err
	}

	return api.GetArtifact200JSONResponse{
		Type: "latest",
		Coordinates: struct {
			ArtifactId string `json:"artifactId"`
			GroupId    string `json:"groupId"`
		}{
			ArtifactId: artifact.ArtifactID,
			GroupId:    artifact.GroupID,
		},
		DisplayName:   &artifact.DisplayName,
		GitRepository: &artifact.GitRepositories,
		Website:       artifact.Website,
		Issues:        artifact.Issues,
		Tags:          tags,
	}, nil
}

func (h *Handler) GetVersions(ctx context.Context, request api.GetVersionsRequestObject) (api.GetVersionsResponseObject, error) {
	return nil, nil
}

func (h *Handler) GetVersionInfo(ctx context.Context, request api.GetVersionInfoRequestObject) (api.GetVersionInfoResponseObject, error) {
	return nil, nil
}
