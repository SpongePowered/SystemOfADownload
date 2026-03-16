package httpapi

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"strings"

	"go.temporal.io/sdk/client"

	"github.com/spongepowered/systemofadownload/api"
	"github.com/spongepowered/systemofadownload/internal/app"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/repository"
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
	limit := int32(25)
	if request.Params.Limit != nil {
		l := int32(*request.Params.Limit)
		if l < 1 || l > 25 {
			return NewGetVersionsBadRequestError("limit must be between 1 and 25"), nil
		}
		limit = l
	}

	offset := int32(0)
	if request.Params.Offset != nil {
		o := int32(*request.Params.Offset)
		if o < 0 {
			return NewGetVersionsBadRequestError("offset must be non-negative"), nil
		}
		offset = o
	}

	var tags map[string]string
	if request.Params.Tags != nil && *request.Params.Tags != "" {
		var err error
		tags, err = parseTags(*request.Params.Tags)
		if err != nil {
			return NewGetVersionsBadRequestError(fmt.Sprintf("invalid tags: %s", err)), nil
		}
	}

	slog.DebugContext(ctx, "GetVersions",
		"groupID", request.GroupID,
		"artifactID", request.ArtifactID,
		"limit", limit, "offset", offset,
		"tags", tags,
		"recommended", request.Params.Recommended)

	entries, err := h.service.GetVersions(ctx, repository.VersionQueryParams{
		GroupID:     request.GroupID,
		ArtifactID:  request.ArtifactID,
		Recommended: request.Params.Recommended,
		Tags:        tags,
		Limit:       limit,
		Offset:      offset,
	})
	if err != nil {
		if errors.Is(err, app.ErrArtifactNotFound) {
			return api.GetVersions404Response{}, nil
		}
		slog.ErrorContext(ctx, "failed to get versions",
			"groupID", request.GroupID,
			"artifactID", request.ArtifactID,
			"error", err)
		return nil, err
	}

	type versionDetail = struct {
		Recommended *bool              `json:"recommended,omitempty"`
		TagValues   *map[string]string `json:"tagValues,omitempty"`
	}

	artifacts := make(map[string]versionDetail, len(entries))
	for _, e := range entries {
		rec := e.Recommended
		var tagVals *map[string]string
		if len(e.Tags) > 0 {
			tagVals = &e.Tags
		}
		artifacts[e.Version] = versionDetail{
			Recommended: &rec,
			TagValues:   tagVals,
		}
	}

	return api.GetVersions200JSONResponse{
		Artifacts: &artifacts,
	}, nil
}

// parseTags parses a comma-separated list of key:value tag pairs.
func parseTags(raw string) (map[string]string, error) {
	tags := make(map[string]string)
	for _, pair := range strings.Split(raw, ",") {
		pair = strings.TrimSpace(pair)
		if pair == "" {
			continue
		}
		idx := strings.IndexByte(pair, ':')
		if idx <= 0 || idx == len(pair)-1 {
			return nil, fmt.Errorf("malformed tag pair %q, expected key:value", pair)
		}
		tags[strings.TrimSpace(pair[:idx])] = strings.TrimSpace(pair[idx+1:])
	}
	if len(tags) == 0 {
		return nil, fmt.Errorf("no valid tag pairs found")
	}
	return tags, nil
}

func (h *Handler) GetVersionInfo(ctx context.Context, request api.GetVersionInfoRequestObject) (api.GetVersionInfoResponseObject, error) {
	return nil, nil
}
