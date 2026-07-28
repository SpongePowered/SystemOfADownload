package activity

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"slices"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	temporalactivity "go.temporal.io/sdk/activity"
	"go.temporal.io/sdk/temporal"

	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/githubapi"
	"github.com/spongepowered/systemofadownload/internal/repository"
)

// githubUserCacheTTL applies to hits and misses alike; a commit that GitHub has
// no account for is a stable answer worth remembering for as long as a hit.
const githubUserCacheTTL = 30 * 24 * time.Hour

// errUnresolvableVersion marks a version whose stored state cannot be resolved.
// Retrying will not help, so the batch skips it without stamping the marker.
var errUnresolvableVersion = errors.New("version cannot be resolved")

type gitHubAuthorResolver interface {
	ResolveUsername(ctx context.Context, repoURL, sha, email string) (string, error)
}

// GitHubAuthorResolutionActivities owns durable GitHub author lookup and persistence.
type GitHubAuthorResolutionActivities struct {
	Repo   repository.Repository
	GitHub gitHubAuthorResolver
}

// FetchVersionsNeedingAuthorResolutionInput selects one newest-first keyset page.
type FetchVersionsNeedingAuthorResolutionInput struct {
	BeforeID *int64
	PageSize int32
}

// FetchVersionsNeedingAuthorResolution returns enriched versions whose author
// resolution marker is missing or stale, newest first. Only IDs cross the
// activity boundary; commit bodies never enter workflow history.
func (a *GitHubAuthorResolutionActivities) FetchVersionsNeedingAuthorResolution(
	ctx context.Context,
	input FetchVersionsNeedingAuthorResolutionInput,
) ([]int64, error) {
	ids, err := a.Repo.ListVersionsNeedingGitHubAuthorResolution(
		ctx,
		db.ListVersionsNeedingGitHubAuthorResolutionParams{
			BeforeID: input.BeforeID,
			PageSize: input.PageSize,
		},
	)
	if err != nil {
		return nil, fmt.Errorf("listing versions needing GitHub author resolution: %w", err)
	}
	return ids, nil
}

// ResolveGitHubAuthorsBatchInput is one page of artifact version IDs.
type ResolveGitHubAuthorsBatchInput struct {
	VersionIDs []int64
}

// ResolveGitHubAuthorsBatchOutput reports counts only, never commit payloads.
type ResolveGitHubAuthorsBatchOutput struct {
	VersionsStamped   int
	AuthorsResolved   int
	AuthorsUnresolved int
}

type githubResolutionHeartbeat struct {
	NextIndex int
}

// pendingVersion is a version awaiting the merge phase.
type pendingVersion struct {
	Index      int
	VersionID  int64
	References []githubAuthorReference
}

// ResolveGitHubAuthorsBatch resolves and persists authors for one page. GitHub
// lookups are deduplicated across the whole page and served from a shared cache
// first, so a page usually costs no API calls at all.
func (a *GitHubAuthorResolutionActivities) ResolveGitHubAuthorsBatch(
	ctx context.Context,
	input ResolveGitHubAuthorsBatchInput,
) (*ResolveGitHubAuthorsBatchOutput, error) {
	start := 0
	var heartbeat githubResolutionHeartbeat
	if temporalactivity.HasHeartbeatDetails(ctx) {
		if err := temporalactivity.GetHeartbeatDetails(ctx, &heartbeat); err == nil {
			start = min(max(heartbeat.NextIndex, 0), len(input.VersionIDs))
		}
	}

	pending, references, err := a.collectPageAuthors(ctx, input.VersionIDs, start)
	if err != nil {
		return nil, err
	}

	resolutions, err := a.resolvePageAuthors(ctx, references, start)
	if err != nil {
		return nil, err
	}

	output := &ResolveGitHubAuthorsBatchOutput{}
	for _, version := range pending {
		merged, err := a.mergeVersionAuthors(ctx, version.VersionID, resolutions)
		if err != nil {
			return nil, fmt.Errorf("merging GitHub authors for version %d: %w", version.VersionID, err)
		}
		if merged.Stamped {
			output.VersionsStamped++
			output.AuthorsResolved += merged.Resolved
			output.AuthorsUnresolved += merged.Unresolved
		}
		temporalactivity.RecordHeartbeat(ctx, githubResolutionHeartbeat{NextIndex: version.Index + 1})
	}
	return output, nil
}

// collectPageAuthors reads every version in the page without locking and
// deduplicates their authors, so the GitHub phase never holds a row lock.
func (a *GitHubAuthorResolutionActivities) collectPageAuthors(
	ctx context.Context,
	versionIDs []int64,
	start int,
) ([]pendingVersion, map[string]githubAuthorReference, error) {
	var pending []pendingVersion
	references := make(map[string]githubAuthorReference)

	for i := start; i < len(versionIDs); i++ {
		versionID := versionIDs[i]
		info, err := a.loadCommitInfo(ctx, versionID)
		if err != nil {
			if errors.Is(err, errUnresolvableVersion) {
				slog.WarnContext(ctx, "skipping GitHub author resolution for version",
					"versionID", versionID,
					"error", err,
				)
				continue
			}
			return nil, nil, fmt.Errorf("reading version %d: %w", versionID, err)
		}
		if info == nil {
			continue
		}

		versionReferences := collectGithubAuthorReferences(info)
		pending = append(pending, pendingVersion{
			Index:      i,
			VersionID:  versionID,
			References: versionReferences,
		})
		for _, ref := range versionReferences {
			references[githubAuthorKey(ref)] = ref
		}
	}
	return pending, references, nil
}

// resolvePageAuthors serves the page's authors from the shared cache in one
// query and only calls GitHub for what is left.
func (a *GitHubAuthorResolutionActivities) resolvePageAuthors(
	ctx context.Context,
	references map[string]githubAuthorReference,
	nextIndex int,
) (map[string]string, error) {
	resolutions, err := a.lookupCachedUsernames(ctx, references)
	if err != nil {
		return nil, err
	}

	keys := make([]string, 0, len(references))
	for key := range references {
		if _, cached := resolutions[key]; !cached {
			keys = append(keys, key)
		}
	}
	slices.Sort(keys)

	for _, key := range keys {
		username, err := a.resolveUncachedAuthor(ctx, references[key])
		if err != nil {
			return nil, resolutionFailure(err)
		}
		resolutions[key] = username
		temporalactivity.RecordHeartbeat(ctx, githubResolutionHeartbeat{NextIndex: nextIndex})
	}
	return resolutions, nil
}

func (a *GitHubAuthorResolutionActivities) lookupCachedUsernames(
	ctx context.Context,
	references map[string]githubAuthorReference,
) (map[string]string, error) {
	emails := make([]string, 0, len(references))
	for key, ref := range references {
		email, ok := strings.CutPrefix(key, githubAuthorEmailKeyPrefix)
		if !ok {
			continue
		}
		// Noreply addresses carry the username, so they never reach the cache.
		if githubapi.UsernameFromNoreplyEmail(normalizeAuthorEmail(ref.Author.Email)) != "" {
			continue
		}
		emails = append(emails, email)
	}
	resolutions := make(map[string]string, len(references))
	if len(emails) == 0 {
		return resolutions, nil
	}
	slices.Sort(emails)

	cached, err := a.Repo.GetGitHubUserCacheBatch(ctx, emails)
	if err != nil {
		return nil, fmt.Errorf("reading GitHub user cache: %w", err)
	}
	for _, entry := range cached {
		username := ""
		if entry.GithubUsername != nil {
			username = *entry.GithubUsername
		}
		resolutions[githubAuthorEmailKeyPrefix+entry.AuthorEmail] = username
	}
	return resolutions, nil
}

func (a *GitHubAuthorResolutionActivities) resolveUncachedAuthor(
	ctx context.Context,
	ref githubAuthorReference,
) (string, error) {
	email := normalizeAuthorEmail(ref.Author.Email)
	if username := githubapi.UsernameFromNoreplyEmail(email); username != "" {
		return username, nil
	}

	username, err := a.GitHub.ResolveUsername(ctx, ref.Repository, ref.Sha, email)
	if err != nil {
		return "", fmt.Errorf("resolving GitHub author: %w", err)
	}
	if email == "" {
		return username, nil
	}
	if err := a.storeGithubUserCache(ctx, email, username); err != nil {
		return "", err
	}
	return username, nil
}

// resolutionFailure lets GitHub rate limits dictate their own retry delay while
// leaving every other failure on the activity's default retry backoff.
func resolutionFailure(err error) error {
	var rateLimitErr *githubapi.RateLimitError
	if !errors.As(err, &rateLimitErr) {
		return err
	}

	delay := max(time.Until(rateLimitErr.ResetAt), time.Second)
	return temporal.NewApplicationErrorWithOptions(
		err.Error(),
		"GithubRateLimit",
		temporal.ApplicationErrorOptions{NextRetryDelay: delay},
	)
}

// loadCommitInfo returns nil when the version is already resolved at the
// current schema and nothing needs doing.
func (a *GitHubAuthorResolutionActivities) loadCommitInfo(
	ctx context.Context,
	versionID int64,
) (*domain.CommitInfo, error) {
	version, err := a.Repo.GetArtifactVersionByID(ctx, versionID)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, fmt.Errorf("%w: no longer exists", errUnresolvableVersion)
		}
		return nil, err
	}

	var info domain.CommitInfo
	if err := json.Unmarshal(version.CommitBody, &info); err != nil {
		return nil, fmt.Errorf("%w: unmarshaling commit body: %w", errUnresolvableVersion, err)
	}
	if authorResolutionCurrent(&info) {
		return nil, nil
	}
	return &info, nil
}

func authorResolutionCurrent(info *domain.CommitInfo) bool {
	return info.AuthorResolution != nil &&
		info.AuthorResolution.Schema >= domain.AuthorResolutionSchema
}

func (a *GitHubAuthorResolutionActivities) storeGithubUserCache(
	ctx context.Context,
	email, username string,
) error {
	return a.Repo.WithTx(ctx, func(tx repository.Tx) error {
		var usernamePtr *string
		if username != "" {
			usernamePtr = &username
		}
		_, err := tx.UpsertGitHubUserCache(ctx, db.UpsertGitHubUserCacheParams{
			AuthorEmail:    email,
			GithubUsername: usernamePtr,
			ExpiresAt: pgtype.Timestamptz{
				Time:  time.Now().Add(githubUserCacheTTL),
				Valid: true,
			},
		})
		return err
	})
}

// versionMergeResult reports what a single locked merge did.
type versionMergeResult struct {
	Stamped    bool
	Resolved   int
	Unresolved int
}

func (a *GitHubAuthorResolutionActivities) mergeVersionAuthors(
	ctx context.Context,
	versionID int64,
	resolutions map[string]string,
) (versionMergeResult, error) {
	var result versionMergeResult
	err := a.Repo.WithTx(ctx, func(tx repository.Tx) error {
		result = versionMergeResult{}

		version, err := tx.GetArtifactVersionForUpdate(ctx, versionID)
		if err != nil {
			return err
		}

		var info domain.CommitInfo
		if err := json.Unmarshal(version.CommitBody, &info); err != nil {
			return fmt.Errorf("unmarshaling locked commit body: %w", err)
		}
		if authorResolutionCurrent(&info) {
			return nil
		}

		for _, ref := range collectGithubAuthorReferences(&info) {
			username, ok := resolutions[githubAuthorKey(ref)]
			if !ok {
				// The body changed while we were resolving; leave the marker
				// off so the next tick picks this version up again.
				return nil
			}
			ref.Author.GitHubUsername = username
			if username == "" {
				result.Unresolved++
			} else {
				result.Resolved++
			}
		}

		info.AuthorResolution = &domain.AuthorResolution{
			At:         time.Now().UTC().Format(time.RFC3339),
			Unresolved: result.Unresolved,
			Schema:     domain.AuthorResolutionSchema,
		}
		data, err := json.Marshal(info)
		if err != nil {
			return fmt.Errorf("marshaling resolved commit body: %w", err)
		}
		if err := tx.UpdateArtifactVersionCommitBody(ctx, db.UpdateArtifactVersionCommitBodyParams{
			ID:         versionID,
			CommitBody: data,
		}); err != nil {
			return err
		}
		result.Stamped = true
		return nil
	})
	if err != nil {
		return versionMergeResult{}, err
	}
	return result, nil
}

type githubAuthorReference struct {
	Author     *domain.CommitAuthor
	Repository string
	Sha        string
}

func collectGithubAuthorReferences(info *domain.CommitInfo) []githubAuthorReference {
	var references []githubAuthorReference
	appendGithubAuthorReference(&references, info.Author, info.Repository, info.Sha)
	for i := range info.Submodules {
		submodule := &info.Submodules[i]
		appendGithubAuthorReference(&references, submodule.Author, submodule.Repository, submodule.Sha)
	}
	collectGithubChangelogReferences(&references, info.Changelog, info.Repository)
	return references
}

func collectGithubChangelogReferences(
	references *[]githubAuthorReference,
	changelog *domain.Changelog,
	repositoryURL string,
) {
	if changelog == nil {
		return
	}
	for i := range changelog.Commits {
		commit := &changelog.Commits[i]
		repoURL := repositoryURL
		if owner, repo, ok := githubapi.ParseRepository(commit.URL); ok {
			repoURL = "https://github.com/" + owner + "/" + repo
		}
		appendGithubAuthorReference(references, commit.Author, repoURL, commit.Sha)
	}
	for submoduleURL, submoduleChangelog := range changelog.SubmoduleChangelogs {
		collectGithubChangelogReferences(references, submoduleChangelog, submoduleURL)
	}
}

func appendGithubAuthorReference(
	references *[]githubAuthorReference,
	author *domain.CommitAuthor,
	repositoryURL, sha string,
) {
	if author == nil {
		return
	}
	*references = append(*references, githubAuthorReference{
		Author:     author,
		Repository: repositoryURL,
		Sha:        sha,
	})
}

const githubAuthorEmailKeyPrefix = "email:"

// githubAuthorKey dedupes authors across the page. Email is the shared cache
// key; authors without one fall back to their commit, which stays local to
// this run.
func githubAuthorKey(ref githubAuthorReference) string {
	if email := normalizeAuthorEmail(ref.Author.Email); email != "" {
		return githubAuthorEmailKeyPrefix + email
	}
	return "commit:" + strings.ToLower(strings.TrimSpace(ref.Repository)) + "@" + strings.TrimSpace(ref.Sha)
}

func normalizeAuthorEmail(email string) string {
	return strings.ToLower(strings.TrimSpace(email))
}
