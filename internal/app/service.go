package app

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"

	"github.com/jackc/pgx/v5"
	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/repository"
)

var (
	ErrGroupAlreadyExists    = errors.New("group already exists")
	ErrGroupNotFound         = errors.New("group not found")
	ErrArtifactAlreadyExists = errors.New("artifact already exists")
	ErrArtifactNotFound      = errors.New("artifact not found")
	ErrVersionNotFound       = errors.New("version not found")
)

// VersionEntry represents a single version in the GetVersions response.
type VersionEntry struct {
	Version     string
	Recommended bool
	Tags        map[string]string
}

type Service struct {
	repo repository.Repository
}

func NewService(repo repository.Repository) *Service {
	return &Service{repo: repo}
}

func (s *Service) GetGroup(ctx context.Context, groupID string) (*domain.Group, error) {
	g, err := s.repo.GetGroup(ctx, groupID)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrGroupNotFound
		}
		return nil, err
	}

	return &domain.Group{
		GroupID: g.MavenID,
		Name:    g.Name,
		Website: g.Website,
	}, nil
}

func (s *Service) ListGroups(ctx context.Context) ([]*domain.Group, error) {
	groups, err := s.repo.ListGroups(ctx)
	if err != nil {
		return nil, err
	}

	var domainGroups []*domain.Group
	for _, g := range groups {
		domainGroups = append(domainGroups, &domain.Group{
			GroupID: g.MavenID,
			Name:    g.Name,
			Website: g.Website,
		})
	}
	return domainGroups, nil
}

func (s *Service) RegisterGroup(ctx context.Context, group *domain.Group) error {
	return s.repo.WithTx(ctx, func(tx repository.Tx) error {
		exists, err := tx.GroupExistsByMavenID(ctx, group.GroupID)
		if err != nil {
			return fmt.Errorf("failed to check if group exists: %w", err)
		}
		if exists {
			return ErrGroupAlreadyExists
		}

		_, err = tx.CreateGroup(ctx, db.CreateGroupParams{
			MavenID: group.GroupID,
			Name:    group.Name,
			Website: group.Website,
		})
		return err
	})
}

func (s *Service) ListArtifacts(ctx context.Context, groupID string) ([]string, error) {
	artifacts, err := s.repo.ListArtifactsByGroup(ctx, groupID)
	if err != nil {
		return nil, err
	}

	var artifactIDs []string
	for _, a := range artifacts {
		artifactIDs = append(artifactIDs, a.ArtifactID)
	}
	return artifactIDs, nil
}

func (s *Service) GetArtifact(ctx context.Context, groupID, artifactID string) (*domain.Artifact, map[string][]string, error) {
	a, err := s.repo.GetArtifactByGroupAndId(ctx, db.GetArtifactByGroupAndIdParams{
		GroupID:    groupID,
		ArtifactID: artifactID,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, nil, ErrArtifactNotFound
		}
		return nil, nil, err
	}

	var gitRepos []string
	if err := json.Unmarshal(a.GitRepositories, &gitRepos); err != nil {
		return nil, nil, fmt.Errorf("unmarshaling git repositories: %w", err)
	}
	if gitRepos == nil {
		gitRepos = []string{}
	}

	artifact := &domain.Artifact{
		GroupID:         a.GroupID,
		ArtifactID:      a.ArtifactID,
		DisplayName:     a.Name,
		Website:         a.Website,
		Issues:          a.Issues,
		GitRepositories: gitRepos,
	}

	tagRows, err := s.repo.ListDistinctTagsByArtifact(ctx, db.ListDistinctTagsByArtifactParams{
		GroupID:    groupID,
		ArtifactID: artifactID,
	})
	if err != nil {
		return nil, nil, fmt.Errorf("listing tags: %w", err)
	}

	tags := make(map[string][]string)
	for _, row := range tagRows {
		tags[row.TagKey] = append(tags[row.TagKey], row.TagValue)
	}

	return artifact, tags, nil
}

func (s *Service) GetVersions(ctx context.Context, params repository.VersionQueryParams) ([]VersionEntry, error) {
	// Check artifact exists
	_, err := s.repo.GetArtifactByGroupAndId(ctx, db.GetArtifactByGroupAndIdParams{
		GroupID:    params.GroupID,
		ArtifactID: params.ArtifactID,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrArtifactNotFound
		}
		return nil, fmt.Errorf("checking artifact: %w", err)
	}

	versions, err := s.repo.ListVersionsFiltered(ctx, params)
	if err != nil {
		return nil, fmt.Errorf("listing versions: %w", err)
	}

	if len(versions) == 0 {
		return []VersionEntry{}, nil
	}

	// Batch-fetch tags for all returned versions
	versionIDs := make([]int64, len(versions))
	for i, v := range versions {
		versionIDs[i] = v.ID
	}

	tagRows, err := s.repo.ListTagsForVersions(ctx, versionIDs)
	if err != nil {
		return nil, fmt.Errorf("listing tags: %w", err)
	}

	tagsByVersion := make(map[int64]map[string]string)
	for _, row := range tagRows {
		if tagsByVersion[row.ArtifactVersionID] == nil {
			tagsByVersion[row.ArtifactVersionID] = make(map[string]string)
		}
		tagsByVersion[row.ArtifactVersionID][row.TagKey] = row.TagValue
	}

	entries := make([]VersionEntry, len(versions))
	for i, v := range versions {
		entries[i] = VersionEntry{
			Version:     v.Version,
			Recommended: v.Recommended,
			Tags:        tagsByVersion[v.ID],
		}
	}

	return entries, nil
}

// VersionAsset represents a single downloadable asset for a version.
type VersionAsset struct {
	Classifier  string
	DownloadURL string
	Sha256      string
}

// VersionDetail holds all data for a single version info response.
type VersionDetail struct {
	GroupID     string
	ArtifactID  string
	Version     string
	Recommended bool
	CommitBody  []byte // raw JSON from DB
	Tags        map[string]string
	Assets      []VersionAsset
}

func (s *Service) GetVersionInfo(ctx context.Context, groupID, artifactID, version string) (*VersionDetail, error) {
	av, err := s.repo.GetArtifactVersion(ctx, db.GetArtifactVersionParams{
		GroupID:    groupID,
		ArtifactID: artifactID,
		Version:    version,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrVersionNotFound
		}
		return nil, fmt.Errorf("getting version: %w", err)
	}

	assets, err := s.repo.ListArtifactVersionAssets(ctx, av.ID)
	if err != nil {
		return nil, fmt.Errorf("listing assets: %w", err)
	}

	tagRows, err := s.repo.ListArtifactVersionTags(ctx, av.ID)
	if err != nil {
		return nil, fmt.Errorf("listing tags: %w", err)
	}

	tags := make(map[string]string, len(tagRows))
	for _, row := range tagRows {
		tags[row.TagKey] = row.TagValue
	}

	versionAssets := make([]VersionAsset, len(assets))
	for i, a := range assets {
		var classifier, sha256 string
		if a.Classifier != nil {
			classifier = *a.Classifier
		}
		if a.Sha256 != nil {
			sha256 = *a.Sha256
		}
		versionAssets[i] = VersionAsset{
			Classifier:  classifier,
			DownloadURL: a.DownloadUrl,
			Sha256:      sha256,
		}
	}

	return &VersionDetail{
		GroupID:     groupID,
		ArtifactID:  artifactID,
		Version:     av.Version,
		Recommended: av.Recommended,
		CommitBody:  av.CommitBody,
		Tags:        tags,
		Assets:      versionAssets,
	}, nil
}

func (s *Service) RegisterArtifact(ctx context.Context, artifact *domain.Artifact) error {
	return s.repo.WithTx(ctx, func(tx repository.Tx) error {
		_, err := tx.GetGroup(ctx, artifact.GroupID)
		if err != nil {
			if errors.Is(err, pgx.ErrNoRows) {
				return ErrGroupNotFound
			}
			return fmt.Errorf("failed to check if group exists: %w", err)
		}

		_, err = tx.GetArtifactByGroupAndId(ctx, db.GetArtifactByGroupAndIdParams{
			GroupID:    artifact.GroupID,
			ArtifactID: artifact.ArtifactID,
		})
		if err == nil {
			return ErrArtifactAlreadyExists
		}
		if !errors.Is(err, pgx.ErrNoRows) {
			return fmt.Errorf("failed to check if artifact exists: %w", err)
		}

		gitReposJSON, err := json.Marshal(artifact.GitRepositories)
		if err != nil {
			return fmt.Errorf("failed to marshal git repositories: %w", err)
		}

		_, err = tx.CreateArtifact(ctx, db.CreateArtifactParams{
			GroupID:         artifact.GroupID,
			ArtifactID:      artifact.ArtifactID,
			Name:            artifact.DisplayName,
			Website:         artifact.Website,
			Issues:          artifact.Issues,
			GitRepositories: gitReposJSON,
		})
		return err
	})
}
