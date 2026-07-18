package activity

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"sync"
	"time"

	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/githubapi"
	"github.com/spongepowered/systemofadownload/internal/repository"
)

// ChangelogActivities provides normal (non-local) activities for
// commit enrichment DB reads and writes.
type ChangelogActivities struct {
	Repo   repository.Repository
	GitHub *githubapi.Client
}

// FetchVersionsForEnrichmentInput is the input for FetchVersionsForEnrichment.
type FetchVersionsForEnrichmentInput struct {
	GroupID    string
	ArtifactID string
}

// VersionForEnrichment holds the data needed to enrich a single version.
type VersionForEnrichment struct {
	ID         int64
	ArtifactID int64
	Version    string
	SortOrder  int32
	CommitSha  string
	Repository string // from commit_body (may be empty)
	Branch     string // from commit_body (may be empty)
}

// FetchVersionsForEnrichmentOutput is the output for FetchVersionsForEnrichment.
type FetchVersionsForEnrichmentOutput struct {
	Versions        []VersionForEnrichment
	GitRepositories []string // from artifact registration
}

// FetchVersionsForEnrichment returns versions that have a commit SHA but no enrichment yet,
// along with the artifact's registered git repositories.
func (a *ChangelogActivities) FetchVersionsForEnrichment(ctx context.Context, input FetchVersionsForEnrichmentInput) (*FetchVersionsForEnrichmentOutput, error) {
	// Look up the artifact's git repositories
	artifact, err := a.Repo.GetArtifactByGroupAndId(ctx, db.GetArtifactByGroupAndIdParams{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
	})
	if err != nil {
		return nil, fmt.Errorf("looking up artifact: %w", err)
	}

	var gitRepos []string
	if err := json.Unmarshal(artifact.GitRepositories, &gitRepos); err != nil {
		return nil, fmt.Errorf("unmarshaling git_repositories: %w", err)
	}

	versions, err := a.Repo.ListVersionsNeedingEnrichment(ctx, db.ListVersionsNeedingEnrichmentParams{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
	})
	if err != nil {
		return nil, fmt.Errorf("listing versions needing enrichment: %w", err)
	}

	result := make([]VersionForEnrichment, 0, len(versions))
	for _, v := range versions {
		var info domain.CommitInfo
		if v.CommitBody != nil {
			if err := json.Unmarshal(v.CommitBody, &info); err != nil {
				continue // skip versions with invalid commit_body
			}
		}
		if info.Sha == "" {
			continue
		}
		result = append(result, VersionForEnrichment{
			ID:         v.ID,
			ArtifactID: v.ArtifactID,
			Version:    v.Version,
			SortOrder:  v.SortOrder,
			CommitSha:  info.Sha,
			Repository: info.Repository,
			Branch:     info.Branch,
		})
	}

	return &FetchVersionsForEnrichmentOutput{
		Versions:        result,
		GitRepositories: gitRepos,
	}, nil
}

// FetchEnrichedVersions returns all versions that have already been enriched,
// along with the artifact's registered git repositories. Used by ForceChangelog
// to re-compute changelogs without re-running the full enrichment pipeline.
func (a *ChangelogActivities) FetchEnrichedVersions(ctx context.Context, input FetchVersionsForEnrichmentInput) (*FetchVersionsForEnrichmentOutput, error) {
	artifact, err := a.Repo.GetArtifactByGroupAndId(ctx, db.GetArtifactByGroupAndIdParams{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
	})
	if err != nil {
		return nil, fmt.Errorf("looking up artifact: %w", err)
	}

	var gitRepos []string
	if err := json.Unmarshal(artifact.GitRepositories, &gitRepos); err != nil {
		return nil, fmt.Errorf("unmarshaling git_repositories: %w", err)
	}

	versions, err := a.Repo.ListEnrichedVersions(ctx, db.ListEnrichedVersionsParams{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
	})
	if err != nil {
		return nil, fmt.Errorf("listing enriched versions: %w", err)
	}

	result := make([]VersionForEnrichment, 0, len(versions))
	for _, v := range versions {
		var info domain.CommitInfo
		if v.CommitBody != nil {
			if err := json.Unmarshal(v.CommitBody, &info); err != nil {
				continue
			}
		}
		if info.Sha == "" {
			continue
		}
		result = append(result, VersionForEnrichment{
			ID:         v.ID,
			ArtifactID: v.ArtifactID,
			Version:    v.Version,
			SortOrder:  v.SortOrder,
			CommitSha:  info.Sha,
			Repository: info.Repository,
			Branch:     info.Branch,
		})
	}

	return &FetchVersionsForEnrichmentOutput{
		Versions:        result,
		GitRepositories: gitRepos,
	}, nil
}

// GetPreviousVersionCommitInput is the input for GetPreviousVersionCommit.
type GetPreviousVersionCommitInput struct {
	ArtifactID int64
	SortOrder  int32
}

// GetPreviousVersionCommitOutput holds the previous version's commit data.
type GetPreviousVersionCommitOutput struct {
	Found      bool
	ID         int64
	Version    string
	CommitInfo *domain.CommitInfo
}

// GetPreviousVersionCommit retrieves the commit info for the version immediately before the given sort order.
func (a *ChangelogActivities) GetPreviousVersionCommit(ctx context.Context, input GetPreviousVersionCommitInput) (*GetPreviousVersionCommitOutput, error) {
	av, err := a.Repo.GetPreviousVersion(ctx, db.GetPreviousVersionParams{
		ArtifactID: input.ArtifactID,
		SortOrder:  input.SortOrder,
	})
	if err != nil {
		// pgx.ErrNoRows means there is no previous version
		return &GetPreviousVersionCommitOutput{Found: false}, nil //nolint:nilerr // intentional: no previous version is not an error
	}

	var info domain.CommitInfo
	if av.CommitBody != nil {
		if err := json.Unmarshal(av.CommitBody, &info); err != nil {
			return nil, fmt.Errorf("unmarshaling previous version commit_body: %w", err)
		}
	}

	return &GetPreviousVersionCommitOutput{
		Found:      true,
		ID:         av.ID,
		Version:    av.Version,
		CommitInfo: &info,
	}, nil
}

// CheckPreviousVersionEnrichedInput is the input for CheckPreviousVersionEnriched.
type CheckPreviousVersionEnrichedInput struct {
	VersionID int64
}

// CheckPreviousVersionEnriched checks whether a version has been enriched.
func (a *ChangelogActivities) CheckPreviousVersionEnriched(ctx context.Context, input CheckPreviousVersionEnrichedInput) (bool, error) {
	return a.Repo.IsVersionEnriched(ctx, input.VersionID)
}

// StoreEnrichedCommitInput is the input for StoreEnrichedCommit.
type StoreEnrichedCommitInput struct {
	VersionID  int64
	CommitInfo domain.CommitInfo
}

// StoreEnrichedCommit writes the enriched commit info back to the DB.
func (a *ChangelogActivities) StoreEnrichedCommit(ctx context.Context, input StoreEnrichedCommitInput) error { //nolint:gocritic // Temporal activity signature requires value type
	a.resolveCommitInfoAuthors(ctx, &input.CommitInfo)

	data, err := json.Marshal(input.CommitInfo)
	if err != nil {
		return fmt.Errorf("marshaling enriched commit: %w", err)
	}

	return a.Repo.WithTx(ctx, func(tx repository.Tx) error {
		return tx.UpdateArtifactVersionCommitBody(ctx, db.UpdateArtifactVersionCommitBodyParams{
			ID:         input.VersionID,
			CommitBody: data,
		})
	})
}

// StoreChangelogInput is the input for StoreChangelog.
type StoreChangelogInput struct {
	VersionID int64
	Changelog domain.Changelog
}

// StoreChangelog reads the current commit_body, merges the changelog into it,
// and writes it back. This preserves the enrichment data already stored.
func (a *ChangelogActivities) StoreChangelog(ctx context.Context, input StoreChangelogInput) error {
	a.resolveChangelogAuthors(ctx, &input.Changelog)

	return a.Repo.WithTx(ctx, func(tx repository.Tx) error {
		av, err := tx.GetArtifactVersionByID(ctx, input.VersionID)
		if err != nil {
			return fmt.Errorf("reading version %d: %w", input.VersionID, err)
		}

		var info domain.CommitInfo
		if av.CommitBody != nil {
			if err := json.Unmarshal(av.CommitBody, &info); err != nil {
				return fmt.Errorf("unmarshaling commit_body: %w", err)
			}
		}

		info.Changelog = &input.Changelog
		// Only clear pending_predecessor status; preserve error statuses
		// (e.g., error_commit_not_found from Phase 1).
		if info.ChangelogStatus == "pending_predecessor" {
			info.ChangelogStatus = ""
		}

		data, err := json.Marshal(info)
		if err != nil {
			return fmt.Errorf("marshaling updated commit_body: %w", err)
		}

		return tx.UpdateArtifactVersionCommitBody(ctx, db.UpdateArtifactVersionCommitBodyParams{
			ID:         input.VersionID,
			CommitBody: data,
		})
	})
}

type authorLookup struct {
	author     *domain.CommitAuthor
	repository string
	sha        string
}

func (a *ChangelogActivities) resolveCommitInfoAuthors(ctx context.Context, info *domain.CommitInfo) {
	lookups := []authorLookup{{
		author:     info.Author,
		repository: info.Repository,
		sha:        info.Sha,
	}}
	for i := range info.Submodules {
		lookups = append(lookups, authorLookup{
			author:     info.Submodules[i].Author,
			repository: info.Submodules[i].Repository,
			sha:        info.Submodules[i].Sha,
		})
	}
	a.resolveAuthors(ctx, lookups)
}

func (a *ChangelogActivities) resolveChangelogAuthors(ctx context.Context, changelog *domain.Changelog) {
	var lookups []authorLookup
	collectChangelogAuthorLookups(changelog, "", &lookups)
	a.resolveAuthors(ctx, lookups)
}

func collectChangelogAuthorLookups(changelog *domain.Changelog, repoURL string, lookups *[]authorLookup) {
	if changelog == nil {
		return
	}
	for i := range changelog.Commits {
		commit := &changelog.Commits[i]
		commitRepo := repoURL
		if owner, repo, ok := githubapi.ParseRepository(commit.URL); ok {
			commitRepo = "https://github.com/" + owner + "/" + repo
		}
		*lookups = append(*lookups, authorLookup{
			author:     commit.Author,
			repository: commitRepo,
			sha:        commit.Sha,
		})
	}
	for subRepo, subChangelog := range changelog.SubmoduleChangelogs {
		collectChangelogAuthorLookups(subChangelog, subRepo, lookups)
	}
}

func (a *ChangelogActivities) resolveAuthors(ctx context.Context, lookups []authorLookup) {
	if a.GitHub == nil || len(lookups) == 0 {
		return
	}

	lookupCtx, cancel, ok := githubLookupContext(ctx)
	if !ok {
		slog.WarnContext(ctx, "skipping GitHub author lookup to preserve database persistence time")
		return
	}
	defer cancel()

	const concurrency = 4
	sem := make(chan struct{}, concurrency)
	var wg sync.WaitGroup
	var errorCount int
	var firstErr error
	var errorMu sync.Mutex

	for i := range lookups {
		lookup := lookups[i]
		if lookup.author == nil || lookup.author.GitHubUsername != "" {
			continue
		}

		wg.Add(1)
		go func() {
			defer wg.Done()
			select {
			case sem <- struct{}{}:
			case <-lookupCtx.Done():
				errorMu.Lock()
				errorCount++
				if firstErr == nil {
					firstErr = lookupCtx.Err()
				}
				errorMu.Unlock()
				return
			}
			defer func() { <-sem }()

			username, err := a.GitHub.ResolveUsername(
				lookupCtx,
				lookup.repository,
				lookup.sha,
				lookup.author.Email,
			)
			if err != nil {
				errorMu.Lock()
				errorCount++
				if firstErr == nil {
					firstErr = err
				}
				errorMu.Unlock()
				return
			}
			lookup.author.GitHubUsername = username
		}()
	}
	wg.Wait()

	if errorCount > 0 {
		slog.WarnContext(ctx, "GitHub author lookup failed; using Git author names",
			"failures", errorCount,
			"error", firstErr,
		)
	}
}

const githubLookupPersistenceReserve = 5 * time.Second

func githubLookupContext(ctx context.Context) (context.Context, context.CancelFunc, bool) {
	if deadline, ok := ctx.Deadline(); ok {
		lookupDeadline := deadline.Add(-githubLookupPersistenceReserve)
		if !time.Now().Before(lookupDeadline) {
			return nil, nil, false
		}
		lookupCtx, cancel := context.WithDeadline(ctx, lookupDeadline)
		return lookupCtx, cancel, true
	}

	lookupCtx, cancel := context.WithTimeout(ctx, 30*time.Second)
	return lookupCtx, cancel, true
}
