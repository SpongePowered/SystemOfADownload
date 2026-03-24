package httpapi

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"strings"
	"time"

	openapi_types "github.com/oapi-codegen/runtime/types"
	enumspb "go.temporal.io/api/enums/v1"
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
			ID:                       workflowID,
			TaskQueue:                workflow.VersionSyncTaskQueue,
			WorkflowIDConflictPolicy: enumspb.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
			WorkflowIDReusePolicy:    enumspb.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE,
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
	detail, err := h.service.GetVersionInfo(ctx, request.GroupID, request.ArtifactID, request.VersionID)
	if err != nil {
		if errors.Is(err, app.ErrVersionNotFound) {
			return api.GetVersionInfo404Response{}, nil
		}
		slog.ErrorContext(ctx, "failed to get version info",
			"groupID", request.GroupID,
			"artifactID", request.ArtifactID,
			"versionID", request.VersionID,
			"error", err)
		return nil, err
	}

	assets := make([]api.Asset, len(detail.Assets))
	for i, a := range detail.Assets {
		classifier := a.Classifier
		downloadURL := a.DownloadURL
		assets[i] = api.Asset{
			Classifier:  &classifier,
			DownloadUrl: &downloadURL,
		}
	}

	var tags *map[string]string
	if len(detail.Tags) > 0 {
		tags = &detail.Tags
	}

	response := api.GetVersionInfo200JSONResponse{
		Coordinates: &struct {
			ArtifactId *string `json:"artifactId,omitempty"`
			GroupId    *string `json:"groupId,omitempty"`
			Version    *string `json:"version,omitempty"`
		}{
			ArtifactId: &detail.ArtifactID,
			GroupId:    &detail.GroupID,
			Version:    &detail.Version,
		},
		Assets:      &assets,
		Tags:        tags,
		Recommended: &detail.Recommended,
	}

	// Parse commit body if present
	if len(detail.CommitBody) > 0 {
		var commitInfo domain.CommitInfo
		if err := json.Unmarshal(detail.CommitBody, &commitInfo); err == nil && commitInfo.Sha != "" {
			commit := commitInfoToAPICommit(&commitInfo)

			// Map submodule commits
			var subCommits *[]api.Commit
			if len(commitInfo.Submodules) > 0 {
				subs := make([]api.Commit, len(commitInfo.Submodules))
				for i, sub := range commitInfo.Submodules {
					subs[i] = submoduleCommitToAPICommit(&sub)
				}
				subCommits = &subs
			}

			response.Commit = &struct {
				Commits *[]struct {
					Commit           *api.Commit   `json:"commit,omitempty"`
					SubmoduleCommits *[]api.Commit `json:"submoduleCommits,omitempty"`
				} `json:"commits,omitempty"`
			}{
				Commits: &[]struct {
					Commit           *api.Commit   `json:"commit,omitempty"`
					SubmoduleCommits *[]api.Commit `json:"submoduleCommits,omitempty"`
				}{
					{Commit: &commit, SubmoduleCommits: subCommits},
				},
			}
		}
	}

	return response, nil
}

func (h *Handler) GetArtifactSchema(ctx context.Context, request api.GetArtifactSchemaRequestObject) (api.GetArtifactSchemaResponseObject, error) {
	schema, err := h.service.GetVersionSchema(ctx, request.GroupID, request.ArtifactID)
	if err != nil {
		if errors.Is(err, app.ErrArtifactNotFound) || errors.Is(err, app.ErrSchemaNotFound) {
			return api.GetArtifactSchema404Response{}, nil
		}
		return nil, err
	}

	return api.GetArtifactSchema200JSONResponse(domainSchemaToAPI(schema)), nil
}

func (h *Handler) PutArtifactSchema(ctx context.Context, request api.PutArtifactSchemaRequestObject) (api.PutArtifactSchemaResponseObject, error) {
	if request.Body == nil {
		return api.PutArtifactSchema400Response{}, nil
	}

	schema := apiSchemaToDomain(request.Body)

	err := h.service.UpdateVersionSchema(ctx, request.GroupID, request.ArtifactID, schema)
	if err != nil {
		if errors.Is(err, app.ErrArtifactNotFound) {
			return api.PutArtifactSchema404Response{}, nil
		}
		if errors.Is(err, app.ErrInvalidSchema) {
			return api.PutArtifactSchema400Response{}, nil
		}
		return nil, err
	}

	// Trigger version ordering recomputation with new schema.
	if h.workflows != nil {
		workflowID := fmt.Sprintf("version-ordering-%s-%s", request.GroupID, request.ArtifactID)
		_, err := h.workflows.ExecuteWorkflow(ctx, client.StartWorkflowOptions{
			ID:                       workflowID,
			TaskQueue:                workflow.VersionSyncTaskQueue,
			WorkflowIDConflictPolicy: enumspb.WORKFLOW_ID_CONFLICT_POLICY_TERMINATE_EXISTING,
			WorkflowIDReusePolicy:    enumspb.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE,
		}, workflow.VersionOrderingWorkflow, workflow.VersionOrderingInput{
			GroupID:    request.GroupID,
			ArtifactID: request.ArtifactID,
		})
		if err != nil {
			slog.ErrorContext(ctx, "failed to trigger version ordering",
				"groupID", request.GroupID,
				"artifactID", request.ArtifactID,
				"error", err)
		}
	}

	return api.PutArtifactSchema200JSONResponse(domainSchemaToAPI(schema)), nil
}

func (h *Handler) UpdateArtifact(ctx context.Context, request api.UpdateArtifactRequestObject) (api.UpdateArtifactResponseObject, error) {
	if request.Body == nil {
		return api.UpdateArtifact404Response{}, nil
	}

	input := app.UpdateArtifactInput{
		DisplayName: request.Body.DisplayName,
		Website:     request.Body.Website,
		Issues:      request.Body.Issues,
	}
	if request.Body.GitRepository != nil {
		input.GitRepositories = *request.Body.GitRepository
	}

	artifact, err := h.service.UpdateArtifact(ctx, request.GroupID, request.ArtifactID, input)
	if err != nil {
		if errors.Is(err, app.ErrArtifactNotFound) {
			return api.UpdateArtifact404Response{}, nil
		}
		return nil, err
	}

	return api.UpdateArtifact200JSONResponse{
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
		Tags:          map[string][]string{},
	}, nil
}

// domainSchemaToAPI converts a domain VersionSchema to the API VersionSchema type.
func domainSchemaToAPI(schema *domain.VersionSchema) api.VersionSchema {
	useMojang := schema.UseMojangManifest
	variants := make([]struct {
		Name     string `json:"name"`
		Pattern  string `json:"pattern"`
		Segments []struct {
			Name    string                                   `json:"name"`
			ParseAs api.VersionSchemaVariantsSegmentsParseAs `json:"parse_as"`
			TagKey  *string                                  `json:"tag_key,omitempty"`
		} `json:"segments"`
	}, len(schema.Variants))

	for i, v := range schema.Variants {
		segments := make([]struct {
			Name    string                                   `json:"name"`
			ParseAs api.VersionSchemaVariantsSegmentsParseAs `json:"parse_as"`
			TagKey  *string                                  `json:"tag_key,omitempty"`
		}, len(v.Segments))
		for j, seg := range v.Segments {
			segments[j].Name = seg.Name
			segments[j].ParseAs = api.VersionSchemaVariantsSegmentsParseAs(seg.ParseAs)
			if seg.TagKey != "" {
				tagKey := seg.TagKey
				segments[j].TagKey = &tagKey
			}
		}
		variants[i].Name = v.Name
		variants[i].Pattern = v.Pattern
		variants[i].Segments = segments
	}

	return api.VersionSchema{
		UseMojangManifest: &useMojang,
		Variants:          variants,
	}
}

// apiSchemaToDomain converts an API VersionSchema to the domain VersionSchema type.
func apiSchemaToDomain(schema *api.VersionSchema) *domain.VersionSchema {
	useMojang := false
	if schema.UseMojangManifest != nil {
		useMojang = *schema.UseMojangManifest
	}

	variants := make([]domain.VersionFormatVariant, len(schema.Variants))
	for i, v := range schema.Variants {
		segments := make([]domain.SegmentRule, len(v.Segments))
		for j, seg := range v.Segments {
			segments[j] = domain.SegmentRule{
				Name:    seg.Name,
				ParseAs: string(seg.ParseAs),
			}
			if seg.TagKey != nil {
				segments[j].TagKey = *seg.TagKey
			}
		}
		variants[i] = domain.VersionFormatVariant{
			Name:     v.Name,
			Pattern:  v.Pattern,
			Segments: segments,
		}
	}

	return &domain.VersionSchema{
		UseMojangManifest: useMojang,
		Variants:          variants,
	}
}

func commitInfoToAPICommit(info *domain.CommitInfo) api.Commit {
	sha := info.Sha
	link := info.Repository
	c := api.Commit{
		Sha:  &sha,
		Link: &link,
	}
	if info.Message != "" {
		c.Message = &info.Message
	}
	if info.Body != "" {
		c.Body = &info.Body
	}
	if info.CommitDate != "" {
		date := openapi_types.Date{Time: parseDate(info.CommitDate)}
		if !date.Time.IsZero() {
			c.CommitDate = &date
		}
	}
	if info.Author != nil {
		c.Author = &struct {
			Email *string `json:"email,omitempty"`
			Name  *string `json:"name,omitempty"`
		}{
			Name:  &info.Author.Name,
			Email: &info.Author.Email,
		}
	}
	return c
}

func submoduleCommitToAPICommit(sub *domain.SubmoduleCommit) api.Commit {
	sha := sub.Sha
	link := sub.Repository
	c := api.Commit{
		Sha:  &sha,
		Link: &link,
	}
	if sub.Message != "" {
		c.Message = &sub.Message
	}
	if sub.CommitDate != "" {
		date := openapi_types.Date{Time: parseDate(sub.CommitDate)}
		if !date.Time.IsZero() {
			c.CommitDate = &date
		}
	}
	if sub.Author != nil {
		c.Author = &struct {
			Email *string `json:"email,omitempty"`
			Name  *string `json:"name,omitempty"`
		}{
			Name:  &sub.Author.Name,
			Email: &sub.Author.Email,
		}
	}
	return c
}

func parseDate(s string) time.Time {
	for _, layout := range []string{
		time.RFC3339,
		"2006-01-02T15:04:05-07:00",
		"2006-01-02",
	} {
		if t, err := time.Parse(layout, s); err == nil {
			return t
		}
	}
	return time.Time{}
}
