package activity

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
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

const (
	githubPositiveCacheTTL = 30 * 24 * time.Hour
	githubNegativeCacheTTL = 6 * time.Hour
)

type gitHubAuthorResolver interface {
	ResolveUsername(ctx context.Context, repoURL, sha, email string) (string, error)
}

// GitHubAuthorResolutionActivities owns durable GitHub author lookup and persistence.
type GitHubAuthorResolutionActivities struct {
	Repo   repository.Repository
	GitHub gitHubAuthorResolver
}

// FetchGitHubAuthorResolutionPageInput selects one newest-first keyset page.
type FetchGitHubAuthorResolutionPageInput struct {
	BeforeID *int64
	PageSize int32
}

// FetchGitHubAuthorResolutionPageOutput contains version IDs and the next cursor.
type FetchGitHubAuthorResolutionPageOutput struct {
	VersionIDs   []int64
	NextBeforeID *int64
}

// FetchGitHubAuthorResolutionPage discovers enriched versions that still need resolution.
func (a *GitHubAuthorResolutionActivities) FetchGitHubAuthorResolutionPage(
	ctx context.Context,
	input FetchGitHubAuthorResolutionPageInput,
) (*FetchGitHubAuthorResolutionPageOutput, error) {
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

	output := &FetchGitHubAuthorResolutionPageOutput{VersionIDs: ids}
	if len(ids) > 0 {
		next := ids[len(ids)-1]
		output.NextBeforeID = &next
	}
	return output, nil
}

// ResolveGitHubAuthorsBatchInput is one page of artifact version IDs.
type ResolveGitHubAuthorsBatchInput struct {
	VersionIDs []int64
}

type githubResolutionHeartbeat struct {
	NextIndex int
}

// ResolveGitHubAuthorsBatch resolves and persists authors for one page.
func (a *GitHubAuthorResolutionActivities) ResolveGitHubAuthorsBatch(
	ctx context.Context,
	input ResolveGitHubAuthorsBatchInput,
) error {
	start := 0
	var heartbeat githubResolutionHeartbeat
	if temporalactivity.HasHeartbeatDetails(ctx) {
		if err := temporalactivity.GetHeartbeatDetails(ctx, &heartbeat); err == nil {
			start = heartbeat.NextIndex
		}
	}

	for i := start; i < len(input.VersionIDs); i++ {
		versionID := input.VersionIDs[i]
		resolutions, err := a.resolveVersionAuthors(ctx, versionID, i)
		if err != nil {
			if temporalactivity.GetInfo(ctx).Attempt < 3 {
				var rateLimitErr *githubapi.RateLimitError
				if errors.As(err, &rateLimitErr) {
					delay := time.Until(rateLimitErr.ResetAt)
					if delay < time.Second {
						delay = time.Second
					}
					return temporal.NewApplicationErrorWithOptions(
						err.Error(),
						"GithubRateLimit",
						temporal.ApplicationErrorOptions{NextRetryDelay: delay},
					)
				}
				return temporal.NewApplicationError(err.Error(), "GithubAuthorResolution")
			}

			slog.WarnContext(ctx, "skipping GitHub author resolution after retries",
				"versionID", versionID,
				"error", err,
			)
			temporalactivity.RecordHeartbeat(ctx, githubResolutionHeartbeat{NextIndex: i + 1})
			continue
		}

		if err := a.mergeVersionAuthors(ctx, versionID, resolutions); err != nil {
			return fmt.Errorf("merging GitHub authors for version %d: %w", versionID, err)
		}
		temporalactivity.RecordHeartbeat(ctx, githubResolutionHeartbeat{NextIndex: i + 1})
	}

	return nil
}

type githubAuthorReference struct {
	Author     *domain.CommitAuthor
	Repository string
	Sha        string
}

func (a *GitHubAuthorResolutionActivities) resolveVersionAuthors(
	ctx context.Context,
	versionID int64,
	versionIndex int,
) (map[string]string, error) {
	version, err := a.Repo.GetArtifactVersionByID(ctx, versionID)
	if err != nil {
		return nil, fmt.Errorf("reading version: %w", err)
	}

	var info domain.CommitInfo
	if err := json.Unmarshal(version.CommitBody, &info); err != nil {
		return nil, fmt.Errorf("unmarshaling commit body: %w", err)
	}
	if info.GitHubAuthorsResolvedAt != "" {
		return map[string]string{}, nil
	}

	references := collectGithubAuthorReferences(&info)
	resolutions := make(map[string]string, len(references))
	for _, ref := range references {
		key := githubAuthorKey(ref)
		if _, ok := resolutions[key]; ok {
			continue
		}

		username, err := a.resolveGithubAuthor(ctx, ref)
		if err != nil {
			return nil, err
		}
		resolutions[key] = username
		temporalactivity.RecordHeartbeat(ctx, githubResolutionHeartbeat{NextIndex: versionIndex})
	}
	return resolutions, nil
}

func (a *GitHubAuthorResolutionActivities) resolveGithubAuthor(
	ctx context.Context,
	ref githubAuthorReference,
) (string, error) {
	email := normalizeAuthorEmail(ref.Author.Email)
	if username := githubapi.UsernameFromNoreplyEmail(email); username != "" {
		return username, nil
	}

	if email != "" {
		cached, err := a.Repo.GetGitHubUserCache(ctx, email)
		if err == nil {
			if cached.GithubUsername == nil {
				return "", nil
			}
			return *cached.GithubUsername, nil
		}
		if !errors.Is(err, pgx.ErrNoRows) {
			return "", fmt.Errorf("reading GitHub user cache: %w", err)
		}
	}

	username, err := a.GitHub.ResolveUsername(ctx, ref.Repository, ref.Sha, email)
	if err != nil {
		return "", fmt.Errorf("resolving GitHub author: %w", err)
	}
	if email != "" {
		ttl := githubPositiveCacheTTL
		if username == "" {
			ttl = githubNegativeCacheTTL
		}
		if err := a.storeGithubUserCache(ctx, email, username, ttl); err != nil {
			return "", err
		}
	}
	return username, nil
}

func (a *GitHubAuthorResolutionActivities) storeGithubUserCache(
	ctx context.Context,
	email, username string,
	ttl time.Duration,
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
				Time:  time.Now().Add(ttl),
				Valid: true,
			},
		})
		return err
	})
}

func (a *GitHubAuthorResolutionActivities) mergeVersionAuthors(
	ctx context.Context,
	versionID int64,
	resolutions map[string]string,
) error {
	return a.Repo.WithTx(ctx, func(tx repository.Tx) error {
		version, err := tx.GetArtifactVersionForUpdate(ctx, versionID)
		if err != nil {
			return err
		}

		var info domain.CommitInfo
		if err := json.Unmarshal(version.CommitBody, &info); err != nil {
			return fmt.Errorf("unmarshaling locked commit body: %w", err)
		}
		if info.GitHubAuthorsResolvedAt != "" {
			return nil
		}

		references := collectGithubAuthorReferences(&info)
		for _, ref := range references {
			username, ok := resolutions[githubAuthorKey(ref)]
			if !ok {
				return nil
			}
			ref.Author.GitHubUsername = username
		}

		info.GitHubAuthorsResolvedAt = time.Now().UTC().Format(time.RFC3339)
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
		return nil
	})
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

func githubAuthorKey(ref githubAuthorReference) string {
	if email := normalizeAuthorEmail(ref.Author.Email); email != "" {
		return "email:" + email
	}
	return "commit:" + strings.ToLower(strings.TrimSpace(ref.Repository)) + "@" + strings.TrimSpace(ref.Sha)
}

func normalizeAuthorEmail(email string) string {
	return strings.ToLower(strings.TrimSpace(email))
}
