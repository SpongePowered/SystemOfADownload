package activity

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/repository"
)

// ChangelogActivities provides normal (non-local) activities for
// commit enrichment DB reads and writes.
type ChangelogActivities struct {
	Repo repository.Repository
}

// FetchVersionsForEnrichmentInput is the input for FetchVersionsForEnrichment.
type FetchVersionsForEnrichmentInput struct {
	GroupID    string
	ArtifactID string
}

// VersionForEnrichment holds the data needed to enrich a single version.
type VersionForEnrichment struct {
	ID        int64
	ArtifactID int64
	Version   string
	SortOrder int32
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
		return &GetPreviousVersionCommitOutput{Found: false}, nil
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
func (a *ChangelogActivities) StoreEnrichedCommit(ctx context.Context, input StoreEnrichedCommitInput) error {
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
		info.ChangelogStatus = "" // clear pending status

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
