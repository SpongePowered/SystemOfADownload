package httpapi

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"strings"
	"time"

	openapi_types "github.com/oapi-codegen/runtime/types"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/metric"
	enumspb "go.temporal.io/api/enums/v1"
	"go.temporal.io/api/serviceerror"
	"go.temporal.io/sdk/client"

	"github.com/spongepowered/systemofadownload/api"
	"github.com/spongepowered/systemofadownload/internal/app"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/repository"
	"github.com/spongepowered/systemofadownload/internal/workflow"
)

var apiMeter = otel.Meter("soad.httpapi")

// VersionSyncScheduleID returns the Temporal Schedule ID for a given artifact's
// periodic version sync. Used by both RegisterArtifact (schedule creation) and
// TriggerSync (on-demand trigger).
func VersionSyncScheduleID(groupID, artifactID string) string {
	return fmt.Sprintf("version-sync-%s-%s", groupID, artifactID)
}

// WorkflowStarter is a narrow interface for starting Temporal workflows.
type WorkflowStarter interface {
	ExecuteWorkflow(ctx context.Context, options client.StartWorkflowOptions, workflow interface{}, args ...interface{}) (client.WorkflowRun, error)
}

type Handler struct {
	service    *app.Service
	workflows  WorkflowStarter
	schedules  client.ScheduleClient
	reqCounter metric.Int64Counter
}

func NewHandler(service *app.Service, workflows WorkflowStarter, schedules client.ScheduleClient) *Handler {
	counter, _ := apiMeter.Int64Counter("soad.api.requests",
		metric.WithDescription("Total API requests by operation"),
	)
	return &Handler{service: service, workflows: workflows, schedules: schedules, reqCounter: counter}
}

func (h *Handler) countRequest(ctx context.Context, operation string) {
	h.reqCounter.Add(ctx, 1, metric.WithAttributes(attribute.String("operation", operation)))
}

func (h *Handler) GetGroups(ctx context.Context, _ api.GetGroupsRequestObject) (api.GetGroupsResponseObject, error) {
	h.countRequest(ctx, "GetGroups")
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
	h.countRequest(ctx, "RegisterGroup")
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
	h.countRequest(ctx, "GetGroup")
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
	h.countRequest(ctx, "GetArtifacts")
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
	h.countRequest(ctx, "RegisterArtifact")
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

	// Create a Temporal Schedule for periodic version syncing.
	// TriggerImmediately runs the first sync now; the 2-minute interval handles ongoing polling.
	// BUFFER_ONE ensures a missed tick runs once the current sync finishes.
	if h.schedules != nil {
		scheduleID := VersionSyncScheduleID(request.GroupID, request.Body.ArtifactId)
		_, err := h.schedules.Create(ctx, client.ScheduleOptions{
			ID: scheduleID,
			Spec: client.ScheduleSpec{
				Intervals: []client.ScheduleIntervalSpec{{Every: 2 * time.Minute}},
				Jitter:    15 * time.Second,
			},
			Action: &client.ScheduleWorkflowAction{
				Workflow:  workflow.VersionSyncWorkflow,
				Args:      []any{workflow.VersionSyncInput{GroupID: request.GroupID, ArtifactID: request.Body.ArtifactId}},
				TaskQueue: workflow.VersionSyncTaskQueue,
			},
			Overlap:            enumspb.SCHEDULE_OVERLAP_POLICY_BUFFER_ONE,
			TriggerImmediately: true,
		})
		if err != nil {
			slog.ErrorContext(ctx, "failed to create version sync schedule",
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

func (h *Handler) TriggerSync(ctx context.Context, request api.TriggerSyncRequestObject) (api.TriggerSyncResponseObject, error) {
	h.countRequest(ctx, "TriggerSync")

	if h.schedules == nil {
		slog.ErrorContext(ctx, "schedule client not configured")
		return api.TriggerSync500Response{}, nil
	}

	scheduleID := VersionSyncScheduleID(request.GroupID, request.ArtifactID)
	handle := h.schedules.GetHandle(ctx, scheduleID)
	err := handle.Trigger(ctx, client.ScheduleTriggerOptions{})
	if err != nil {
		var notFound *serviceerror.NotFound
		if errors.As(err, &notFound) {
			slog.WarnContext(ctx, "no sync schedule found",
				"groupID", request.GroupID,
				"artifactID", request.ArtifactID,
			)
			return api.TriggerSync404Response{}, nil
		}
		slog.ErrorContext(ctx, "failed to trigger sync schedule",
			"groupID", request.GroupID,
			"artifactID", request.ArtifactID,
			"error", err,
		)
		return api.TriggerSync500Response{}, nil
	}

	slog.InfoContext(ctx, "sync triggered",
		"groupID", request.GroupID,
		"artifactID", request.ArtifactID,
	)
	msg := "sync triggered"
	return api.TriggerSync200JSONResponse{Message: &msg}, nil
}

func (h *Handler) GetArtifact(ctx context.Context, request api.GetArtifactRequestObject) (api.GetArtifactResponseObject, error) {
	h.countRequest(ctx, "GetArtifact")
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
	h.countRequest(ctx, "GetVersions")
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

	result, err := h.service.GetVersions(ctx, repository.VersionQueryParams{
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

	resp := &orderedVersionsResponse{
		Entries: make([]orderedVersionEntry, len(result.Entries)),
		Offset:  offset,
		Limit:   limit,
		Total:   int32(result.Total),
	}
	for i, e := range result.Entries {
		resp.Entries[i] = orderedVersionEntry{
			Version:     e.Version,
			Recommended: e.Recommended,
			Tags:        e.Tags,
		}
	}

	return resp, nil
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

type orderedVersionEntry struct {
	Version     string
	Recommended bool
	Tags        map[string]string
}

// orderedVersionsResponse writes the versions map as ordered JSON, preserving
// sort_order DESC from the database. Go maps lose insertion order, so this
// writes the JSON manually.
type orderedVersionsResponse struct {
	Entries []orderedVersionEntry
	Offset  int32
	Limit   int32
	Total   int32
}

func (r *orderedVersionsResponse) VisitGetVersionsResponse(w http.ResponseWriter) error {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(200)

	// Build ordered JSON manually
	_, _ = w.Write([]byte(`{"artifacts":{`))
	for i, e := range r.Entries {
		if i > 0 {
			_, _ = w.Write([]byte(`,`))
		}
		// Write key
		keyJSON, _ := json.Marshal(e.Version)
		_, _ = w.Write(keyJSON)
		_, _ = w.Write([]byte(`:{`))

		// Write recommended
		_, _ = w.Write([]byte(`"recommended":`))
		if e.Recommended {
			_, _ = w.Write([]byte(`true`))
		} else {
			_, _ = w.Write([]byte(`false`))
		}

		// Write tagValues
		if len(e.Tags) > 0 {
			tagJSON, _ := json.Marshal(e.Tags)
			_, _ = w.Write([]byte(`,"tagValues":`))
			_, _ = w.Write(tagJSON)
		}

		_, _ = w.Write([]byte(`}`))
	}
	_, _ = w.Write([]byte(`}`))
	_, _ = fmt.Fprintf(w, `,"offset":%d,"limit":%d,"size":%d`, r.Offset, r.Limit, r.Total)
	_, _ = w.Write([]byte("}\n"))
	return nil
}

func (h *Handler) GetLatestVersion(ctx context.Context, request api.GetLatestVersionRequestObject) (api.GetLatestVersionResponseObject, error) {
	h.countRequest(ctx, "GetLatestVersion")
	var tags map[string]string
	if request.Params.Tags != nil && *request.Params.Tags != "" {
		var err error
		tags, err = parseTags(*request.Params.Tags)
		if err != nil {
			return api.GetLatestVersion404Response{}, nil //nolint:nilerr // intentional: invalid tags returns 404
		}
	}

	result, err := h.service.GetVersions(ctx, repository.VersionQueryParams{
		GroupID:     request.GroupID,
		ArtifactID:  request.ArtifactID,
		Recommended: request.Params.Recommended,
		Tags:        tags,
		Limit:       1,
		Offset:      0,
	})
	if err != nil {
		if errors.Is(err, app.ErrArtifactNotFound) {
			return api.GetLatestVersion404Response{}, nil
		}
		return nil, err
	}
	if len(result.Entries) == 0 {
		return api.GetLatestVersion404Response{}, nil
	}

	// Get the full version detail for the latest version
	detail, err := h.service.GetVersionInfo(ctx, request.GroupID, request.ArtifactID, result.Entries[0].Version)
	if err != nil {
		return api.GetLatestVersion404Response{}, nil //nolint:nilerr // intentional: converts error to 404
	}

	return api.GetLatestVersion200JSONResponse(buildVersionInfoResponse(detail)), nil
}

func (h *Handler) GetVersionInfo(ctx context.Context, request api.GetVersionInfoRequestObject) (api.GetVersionInfoResponseObject, error) {
	h.countRequest(ctx, "GetVersionInfo")
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

	return api.GetVersionInfo200JSONResponse(buildVersionInfoResponse(detail)), nil
}

func buildVersionInfoResponse(detail *app.VersionDetail) api.VersionInfo {
	assets := make([]api.Asset, len(detail.Assets))
	for i, a := range detail.Assets {
		classifier := a.Classifier
		downloadURL := a.DownloadURL
		assets[i] = api.Asset{
			Classifier:  &classifier,
			DownloadUrl: &downloadURL,
		}
		if a.Md5 != "" {
			md5 := a.Md5
			assets[i].Md5 = &md5
		}
		if a.Sha1 != "" {
			sha1 := a.Sha1
			assets[i].Sha1 = &sha1
		}
		if a.Extension != "" {
			ext := a.Extension
			assets[i].Extension = &ext
		}
	}

	var tags *map[string]string
	if len(detail.Tags) > 0 {
		tags = &detail.Tags
	}

	response := api.VersionInfo{
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

	if len(detail.CommitBody) > 0 {
		var commitInfo domain.CommitInfo
		if err := json.Unmarshal(detail.CommitBody, &commitInfo); err == nil && commitInfo.Sha != "" {
			type commitEntry = struct {
				Commit           *api.Commit   `json:"commit,omitempty"`
				SubmoduleCommits *[]api.Commit `json:"submoduleCommits,omitempty"`
			}

			var entries []commitEntry

			if cl := commitInfo.Changelog; cl != nil && len(cl.Commits) > 0 {
				for _, cs := range cl.Commits {
					entries = append(entries, commitEntry{
						Commit: commitSummaryToAPICommit(&cs, commitInfo.Repository),
					})
				}
				if len(cl.SubmoduleChangelogs) > 0 && len(entries) > 0 {
					var subs []api.Commit
					for repo, subCL := range cl.SubmoduleChangelogs {
						for _, cs := range subCL.Commits {
							subs = append(subs, *commitSummaryToAPICommit(&cs, repo))
						}
					}
					if len(subs) > 0 {
						entries[0].SubmoduleCommits = &subs
					}
				}
			} else {
				commit := commitInfoToAPICommit(&commitInfo)
				entries = append(entries, commitEntry{Commit: &commit})
			}

			response.Commit = &struct {
				Commits *[]commitEntry `json:"commits,omitempty"`
			}{
				Commits: &entries,
			}
		}
	}

	return response
}

func (h *Handler) GetArtifactSchema(ctx context.Context, request api.GetArtifactSchemaRequestObject) (api.GetArtifactSchemaResponseObject, error) {
	h.countRequest(ctx, "GetArtifactSchema")
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
	h.countRequest(ctx, "PutArtifactSchema")
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
	h.countRequest(ctx, "UpdateArtifact")
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
	link := info.URL
	if link == "" && info.Repository != "" {
		link = domain.CommitURL(info.Repository, info.Sha)
	}
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
		if !date.IsZero() {
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

func commitSummaryToAPICommit(cs *domain.CommitSummary, repo string) *api.Commit {
	sha := cs.Sha
	link := cs.URL
	if link == "" {
		link = domain.CommitURL(repo, cs.Sha)
	}
	c := api.Commit{
		Sha:  &sha,
		Link: &link,
	}
	if cs.Message != "" {
		c.Message = &cs.Message
	}
	if cs.CommitDate != "" {
		date := openapi_types.Date{Time: parseDate(cs.CommitDate)}
		if !date.IsZero() {
			c.CommitDate = &date
		}
	}
	if cs.Author != nil {
		c.Author = &struct {
			Email *string `json:"email,omitempty"`
			Name  *string `json:"name,omitempty"`
		}{
			Name:  &cs.Author.Name,
			Email: &cs.Author.Email,
		}
	}
	return &c
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
