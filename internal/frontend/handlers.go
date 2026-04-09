package frontend

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/repository"
)

func (s *Server) pageData(activeNav string, page any) *PageData {
	return &PageData{
		Platforms: s.platforms,
		Year:      time.Now().Year(),
		Page:      page,
		ActiveNav: activeNav,
		Sponsors:  s.sponsorsJSON,
	}
}

func (s *Server) handleOverview(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	if err := s.templates.RenderOverview(w, s.pageData("", nil)); err != nil {
		slog.ErrorContext(r.Context(), "rendering overview", "error", err)
	}
}

// SettingsPage is the template context for settings.gohtml. It reflects the
// user's currently-stored preferences (from cookies) and whether the user
// just saved the form — the POST handler redirects back with ?saved=1 so the
// "saved" banner survives the PRG (Post/Redirect/Get) cycle.
type SettingsPage struct {
	ShowPreRelease bool
	FilterAPI      bool
	Saved          bool
}

func (s *Server) handleSettings(w http.ResponseWriter, r *http.Request) {
	prefs := ReadPreferences(r)
	page := SettingsPage{
		ShowPreRelease: prefs.ShowPreRelease,
		FilterAPI:      prefs.FilterAPI,
		Saved:          r.URL.Query().Get("saved") == "1",
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	if err := s.templates.RenderSettings(w, s.pageData("settings", page)); err != nil {
		slog.ErrorContext(r.Context(), "rendering settings", "error", err)
	}
}

// settingsFormMaxBytes caps the settings POST body well above the real
// payload (two checkbox fields, ~40 bytes) so memory-exhaustion attacks via
// oversized form bodies fall on the floor before ParseForm allocates.
const settingsFormMaxBytes = 4 * 1024

// handleSettingsSubmit persists the two preference checkboxes as cookies and
// redirects back to /settings with ?saved=1 so a browser refresh after save
// doesn't re-submit the form (PRG pattern).
func (s *Server) handleSettingsSubmit(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, settingsFormMaxBytes)
	if err := r.ParseForm(); err != nil {
		slog.ErrorContext(r.Context(), "parsing settings form", "error", err)
		http.Error(w, "invalid form", http.StatusBadRequest)
		return
	}
	// Unchecked checkboxes are absent from the submitted form, so the
	// absence of a field means "off" rather than "unchanged". This matches
	// normal HTML form semantics and means the stored cookies always
	// mirror what the user sees in the UI.
	prefs := Preferences{
		ShowPreRelease: r.PostForm.Get("prerelease") == "1",
		FilterAPI:      r.PostForm.Get("apifilter") == "1",
	}
	WritePreferences(w, prefs)
	http.Redirect(w, r, "/settings?saved=1", http.StatusSeeOther)
}

func (s *Server) findPlatform(id string) *PlatformConfig {
	for i := range s.platforms {
		if s.platforms[i].ID == id {
			return &s.platforms[i]
		}
	}
	return nil
}

// DownloadsPage holds all template data for the downloads page.
type DownloadsPage struct {
	Platform    PlatformConfig
	MCVersions  []string
	SelectedMC  string
	Recommended *BuildDetail
	Builds      []BuildDetail
	Pagination  PaginationData
}

// BuildDetail holds display data for a single build.
type BuildDetail struct {
	Version      string
	Recommended  bool
	Experimental bool
	Assets       []AssetLink
	Commits      []CommitEntry
	Submodules   []SubmoduleChangelog
	Processing   bool
}

// SubmoduleChangelog holds commits for a single submodule.
type SubmoduleChangelog struct {
	Name    string // short repo name (e.g., "SpongeAPI")
	Commits []CommitEntry
}

// PaginationData holds pagination state for the template.
type PaginationData struct {
	CurrentPage int
	TotalPages  int
	Pages       []PageLink
	HasPrev     bool
	HasNext     bool
	PrevOffset  int
	NextOffset  int
}

// PageLink is a single pagination page button.
type PageLink struct {
	Number int
	Offset int
	Active bool
}

const buildsPerPage = 10

func (s *Server) handleDownloads(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	projectID := r.PathValue("project")
	var timing serverTiming

	platform := s.findPlatform(projectID)
	if platform == nil {
		http.NotFound(w, r)
		return
	}

	// Resolve user preferences: query params override cookies, which
	// override package defaults (hide pre-release MC, filter API versions).
	prefs := ReadPreferences(r)

	// Parse query params
	selectedMC := r.URL.Query().Get("minecraft")
	offsetStr := r.URL.Query().Get("offset")
	offset := 0
	if offsetStr != "" {
		if n, err := strconv.Atoi(offsetStr); err == nil && n >= 0 {
			offset = n
		}
	}

	// Fetch artifact tags and default MC version in parallel.
	// These are independent single-query round-trips.
	var (
		tags       map[string][]string
		defaultMC  string
		tagsErr    error
		defaultErr error
	)

	var wgInit sync.WaitGroup
	wgInit.Add(1)
	go func() {
		defer wgInit.Done()
		phase := timing.start("db-tags", "Artifact tags")
		tags, tagsErr = s.service.GetDistinctTagValues(ctx, platform.Group, platform.ID)
		phase.end()
	}()

	// Only fetch default MC version if not provided in query params
	if selectedMC == "" {
		wgInit.Add(1)
		go func() {
			defer wgInit.Done()
			phase := timing.start("db-default-mc", "Default MC version")
			defaultMC, defaultErr = s.service.GetDefaultTagValue(ctx, platform.Group, platform.ID, "minecraft")
			phase.end()
		}()
	}
	wgInit.Wait()

	if tagsErr != nil {
		slog.ErrorContext(ctx, "fetching artifact tags", "error", tagsErr, "platform", platform.ID)
		http.Error(w, fmt.Sprintf("Failed to load platform data: %v", tagsErr), http.StatusInternalServerError)
		return
	}

	// Filter MC versions (already sorted by sort_order DESC from DB)
	mcVersions := filterMCVersions(tags["minecraft"], prefs.ShowPreRelease)

	if len(mcVersions) == 0 {
		timing.write(w)
		s.renderDownloads(w, platform, nil, "", nil, PaginationData{})
		return
	}

	// Resolve selected MC version
	if selectedMC == "" {
		if defaultErr == nil && defaultMC != "" {
			selectedMC = defaultMC
		} else {
			selectedMC = mcVersions[0]
		}
	}

	// Build tag filter
	tagFilter := map[string]string{"minecraft": selectedMC}
	applyQueryModifiers(platform, selectedMC, prefs.FilterAPI, tagFilter)

	// Fetch recommended + paginated builds in parallel
	var (
		recommended *BuildDetail
		builds      []BuildDetail
		total       int
		recErr      error
		buildErr    error
		wg          sync.WaitGroup
	)

	wg.Add(2)
	go func() {
		defer wg.Done()
		phase := timing.start("db-recommended", "Recommended build")
		recommended, recErr = s.fetchRecommendedBuild(ctx, platform, tagFilter)
		phase.end()
	}()
	go func() {
		defer wg.Done()
		phase := timing.start("db-builds", "Paginated builds")
		builds, total, buildErr = s.fetchPaginatedBuilds(ctx, platform, tagFilter, offset)
		phase.end()
	}()
	wg.Wait()

	if recErr != nil {
		slog.ErrorContext(ctx, "fetching recommended build", "error", recErr)
	}
	if buildErr != nil {
		slog.ErrorContext(ctx, "fetching builds", "error", buildErr)
		http.Error(w, fmt.Sprintf("Failed to load builds: %v", buildErr), http.StatusInternalServerError)
		return
	}

	pagination := computePagination(offset, total)

	renderPhase := timing.start("render", "Template render")
	timing.write(w)
	s.renderDownloads(w, platform, mcVersions, selectedMC, recommended, pagination, builds...)
	renderPhase.end()
	// Log total server timing for observability
	slog.InfoContext(ctx, "downloads page served", "platform", platform.ID, "timing", timing.String())
}

func (s *Server) renderDownloads(
	w http.ResponseWriter,
	platform *PlatformConfig,
	mcVersions []string,
	selectedMC string,
	recommended *BuildDetail,
	pagination PaginationData,
	builds ...BuildDetail,
) {
	page := DownloadsPage{
		Platform:    *platform,
		MCVersions:  mcVersions,
		SelectedMC:  selectedMC,
		Recommended: recommended,
		Builds:      builds,
		Pagination:  pagination,
	}

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	if err := s.templates.RenderDownloads(w, s.pageData(platform.ID, page)); err != nil {
		slog.ErrorContext(context.Background(), "rendering downloads", "error", err)
	}
}

func filterMCVersions(versions []string, showPreRelease bool) []string {
	if showPreRelease {
		result := make([]string, len(versions))
		copy(result, versions)
		return result
	}
	var filtered []string
	for _, v := range versions {
		if !IsPreRelease(v) {
			filtered = append(filtered, v)
		}
	}
	return filtered
}

func applyQueryModifiers(platform *PlatformConfig, mcVersion string, filterAPI bool, tags map[string]string) {
	if !filterAPI {
		return
	}
	if mods, ok := platform.QueryModifiers[mcVersion]; ok {
		for k, v := range mods {
			tags[k] = v
		}
	}
}

func (s *Server) fetchRecommendedBuild(
	ctx context.Context,
	platform *PlatformConfig,
	tagFilter map[string]string,
) (*BuildDetail, error) {
	recommended := true
	result, err := s.service.GetVersionsWithAssets(ctx, repository.VersionQueryParams{
		GroupID:     platform.Group,
		ArtifactID:  platform.ID,
		Recommended: &recommended,
		Tags:        tagFilter,
		Limit:       1,
		Offset:      0,
	})
	if err != nil {
		return nil, err
	}
	if len(result.Versions) == 0 {
		return nil, nil
	}

	mcVersion := tagFilter["minecraft"]
	build := versionWithAssetsToBuild(&result.Versions[0], platform, mcVersion)
	// Recommended section doesn't show commits
	build.Commits = nil
	return &build, nil
}

func (s *Server) fetchPaginatedBuilds(
	ctx context.Context,
	platform *PlatformConfig,
	tagFilter map[string]string,
	offset int,
) ([]BuildDetail, int, error) {
	result, err := s.service.GetVersionsWithAssets(ctx, repository.VersionQueryParams{
		GroupID:    platform.Group,
		ArtifactID: platform.ID,
		Tags:       tagFilter,
		Limit:      buildsPerPage,
		Offset:     int32(offset),
	})
	if err != nil {
		return nil, 0, err
	}
	if len(result.Versions) == 0 {
		return nil, result.Total, nil
	}

	mcVersion := tagFilter["minecraft"]
	builds := make([]BuildDetail, len(result.Versions))
	for i := range result.Versions {
		builds[i] = versionWithAssetsToBuild(&result.Versions[i], platform, mcVersion)
	}
	return builds, result.Total, nil
}

func versionWithAssetsToBuild(v *repository.VersionWithAssets, platform *PlatformConfig, mcVersion string) BuildDetail {
	var assets []assetData
	for _, a := range v.Assets {
		assets = append(assets, assetData{
			Classifier:  a.Classifier,
			DownloadURL: a.DownloadURL,
			Extension:   a.Extension,
		})
	}

	build := BuildDetail{
		Version:      v.Version,
		Recommended:  v.Recommended,
		Experimental: isExperimental(v.Version),
		Assets:       matchAssets(assets, platform.ArtifactTypes, mcVersion, platform.LegacyMCPrefixes),
	}

	build.Commits, build.Submodules, build.Processing = parseCommitBody(v.CommitBody)

	return build
}

func parseCommitBody(raw []byte) ([]CommitEntry, []SubmoduleChangelog, bool) {
	if len(raw) == 0 {
		return nil, nil, false
	}

	var info domain.CommitInfo
	if err := json.Unmarshal(raw, &info); err != nil || info.Sha == "" {
		return nil, nil, false
	}

	// Determine processing state:
	// - Not yet enriched (no EnrichedAt) = processing
	// - Has a pending changelog status = processing
	processing := info.EnrichedAt == "" || info.ChangelogStatus == "pending_predecessor"

	// Build the head commit entry from the full CommitInfo (has body).
	buildHeadEntry := func() CommitEntry {
		link := info.URL
		if link == "" && info.Repository != "" && info.Sha != "" {
			link = domain.CommitURL(info.Repository, info.Sha)
		}
		entry := CommitEntry{
			Message: info.Message,
			Author:  commitAuthorName(info.Author),
			Link:    link,
		}
		entry.Body = deduplicateCommitBody(entry.Message, info.Body)
		entry.HasBody = entry.Body != ""

		if info.CommitDate != "" {
			if t, err := time.Parse(time.RFC3339, info.CommitDate); err == nil {
				entry.RelativeTime = relativeTime(t)
				entry.AbsoluteTime = t.Format("Jan 2, 2006 3:04 PM")
			}
		}
		return entry
	}

	// If there's a changelog with commits, use those.
	// The head commit's body (from CommitInfo) is applied to the first
	// changelog entry, since CommitSummary doesn't carry body data.
	if cl := info.Changelog; cl != nil && len(cl.Commits) > 0 {
		entries := make([]CommitEntry, 0, len(cl.Commits))
		for _, cs := range cl.Commits {
			entry := commitSummaryToEntry(&cs)
			// Build commit URL from repo + sha if not already set
			if entry.Link == "" && info.Repository != "" && cs.Sha != "" {
				entry.Link = domain.CommitURL(info.Repository, cs.Sha)
			}
			entries = append(entries, entry)
		}
		// Apply the head commit body to the first entry if it matches
		if len(entries) > 0 && info.Body != "" {
			body := deduplicateCommitBody(entries[0].Message, info.Body)
			if body != "" {
				entries[0].Body = body
				entries[0].HasBody = true
			}
		}

		// Extract submodule changelogs
		submodules := parseSubmoduleChangelogs(cl.SubmoduleChangelogs)

		return entries, submodules, processing
	}

	// Not yet enriched at all — processing with no commits
	if info.EnrichedAt == "" {
		return nil, nil, true
	}

	// Enriched but no changelog — show the single commit with body
	return []CommitEntry{buildHeadEntry()}, nil, processing
}

func commitSummaryToEntry(cs *domain.CommitSummary) CommitEntry {
	entry := CommitEntry{
		Message: cs.Message,
		Author:  commitAuthorName(cs.Author),
		Link:    cs.URL,
	}
	// CommitSummary doesn't have a separate body field
	entry.HasBody = false

	if cs.CommitDate != "" {
		if t, err := time.Parse(time.RFC3339, cs.CommitDate); err == nil {
			entry.RelativeTime = relativeTime(t)
			entry.AbsoluteTime = t.Format("Jan 2, 2006 3:04 PM")
		}
	}

	return entry
}

func commitAuthorName(author *domain.CommitAuthor) string {
	if author == nil {
		return "Unknown"
	}
	return author.Name
}

// parseSubmoduleChangelogs converts the map of repo URL -> Changelog into
// a sorted slice of SubmoduleChangelog with short names and commit entries.
func parseSubmoduleChangelogs(subs map[string]*domain.Changelog) []SubmoduleChangelog {
	if len(subs) == 0 {
		return nil
	}

	var result []SubmoduleChangelog
	for repoURL, cl := range subs {
		if cl == nil || len(cl.Commits) == 0 {
			continue
		}

		entries := make([]CommitEntry, 0, len(cl.Commits))
		for _, cs := range cl.Commits {
			entry := commitSummaryToEntry(&cs)
			if entry.Link == "" && cs.Sha != "" {
				entry.Link = domain.CommitURL(repoURL, cs.Sha)
			}
			entries = append(entries, entry)
		}

		result = append(result, SubmoduleChangelog{
			Name:    shortRepoName(repoURL),
			Commits: entries,
		})
	}

	return result
}

// shortRepoName extracts a short name from a git repository URL.
// "https://github.com/SpongePowered/SpongeAPI.git" -> "SpongeAPI"
func shortRepoName(repoURL string) string {
	s := strings.TrimRight(repoURL, "/")
	s = strings.TrimSuffix(s, ".git")
	if idx := strings.LastIndex(s, "/"); idx >= 0 {
		return s[idx+1:]
	}
	return s
}

func computePagination(offset, total int) PaginationData {
	if total == 0 {
		return PaginationData{}
	}

	currentPage := (offset / buildsPerPage) + 1
	totalPages := (total + buildsPerPage - 1) / buildsPerPage

	var pages []PageLink
	for i := 1; i <= totalPages; i++ {
		pages = append(pages, PageLink{
			Number: i,
			Offset: (i - 1) * buildsPerPage,
			Active: i == currentPage,
		})
	}

	return PaginationData{
		CurrentPage: currentPage,
		TotalPages:  totalPages,
		Pages:       pages,
		HasPrev:     currentPage > 1,
		HasNext:     currentPage < totalPages,
		PrevOffset:  (currentPage - 2) * buildsPerPage,
		NextOffset:  currentPage * buildsPerPage,
	}
}
