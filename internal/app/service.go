package app

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"regexp"

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
	ErrSchemaNotFound        = errors.New("version schema not set")
	ErrInvalidSchema         = errors.New("invalid version schema")
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

// GetVersionsResult wraps the version entries with total count for pagination.
type GetVersionsResult struct {
	Entries []VersionEntry
	Total   int
}

func (s *Service) GetVersions(ctx context.Context, params repository.VersionQueryParams) (*GetVersionsResult, error) {
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
		return &GetVersionsResult{Entries: []VersionEntry{}, Total: 0}, nil
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

	// Get total count for pagination
	total, err := s.repo.CountVersionsFiltered(ctx, params)
	if err != nil {
		return nil, fmt.Errorf("counting versions: %w", err)
	}

	return &GetVersionsResult{Entries: entries, Total: total}, nil
}

// VersionAsset represents a single downloadable asset for a version.
type VersionAsset struct {
	Classifier  string
	DownloadURL string
	Md5         string
	Sha1        string
	Sha256      string
	Sha512      string
	Extension   string
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

// GetVersionsWithAssets is a frontend-optimized method that returns paginated versions
// with their assets pre-loaded in a single database round-trip.
func (s *Service) GetVersionsWithAssets(ctx context.Context, params repository.VersionQueryParams) (*repository.VersionsWithAssetsResult, error) {
	return s.repo.ListVersionsWithAssets(ctx, params)
}

// GetDistinctTagValues returns the distinct tag values for an artifact, keyed by tag name.
func (s *Service) GetDistinctTagValues(ctx context.Context, groupID, artifactID string) (map[string][]string, error) {
	return s.repo.GetDistinctTagValues(ctx, groupID, artifactID)
}

// GetDefaultTagValue returns a tag value from the latest recommended version of an artifact.
func (s *Service) GetDefaultTagValue(ctx context.Context, groupID, artifactID, tagKey string) (string, error) {
	return s.repo.GetDefaultTagValue(ctx, groupID, artifactID, tagKey)
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
		versionAssets[i] = VersionAsset{
			Classifier:  deref(a.Classifier),
			DownloadURL: a.DownloadUrl,
			Md5:         deref(a.Md5),
			Sha1:        deref(a.Sha1),
			Sha256:      deref(a.Sha256),
			Sha512:      deref(a.Sha512),
			Extension:   deref(a.Extension),
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

// ValidateSchema checks that a VersionSchema is well-formed: patterns compile,
// segments reference named groups, and parse_as values are known.
func ValidateSchema(schema *domain.VersionSchema) error {
	if len(schema.Variants) == 0 {
		return fmt.Errorf("%w: at least one variant is required", ErrInvalidSchema)
	}

	validParseAs := map[string]bool{
		"minecraft": true, "dotted": true, "integer": true, "ignore": true,
	}

	for i, v := range schema.Variants {
		if v.Name == "" {
			return fmt.Errorf("%w: variant %d has no name", ErrInvalidSchema, i)
		}
		if v.Pattern == "" {
			return fmt.Errorf("%w: variant %q has no pattern", ErrInvalidSchema, v.Name)
		}

		re, err := regexp.Compile(v.Pattern)
		if err != nil {
			return fmt.Errorf("%w: variant %q has invalid regex: %w", ErrInvalidSchema, v.Name, err)
		}

		groups := make(map[string]bool)
		for _, name := range re.SubexpNames() {
			if name != "" {
				groups[name] = true
			}
		}

		for _, seg := range v.Segments {
			if !groups[seg.Name] {
				return fmt.Errorf("%w: variant %q segment %q does not match a named capture group (available: %v)",
					ErrInvalidSchema, v.Name, seg.Name, re.SubexpNames())
			}
			if !validParseAs[seg.ParseAs] {
				return fmt.Errorf("%w: variant %q segment %q has unknown parse_as %q",
					ErrInvalidSchema, v.Name, seg.Name, seg.ParseAs)
			}
		}
	}
	return nil
}

// UpdateVersionSchema validates and stores a new version schema for an artifact.
func (s *Service) UpdateVersionSchema(ctx context.Context, groupID, artifactID string, schema *domain.VersionSchema) error {
	if err := ValidateSchema(schema); err != nil {
		return err
	}

	schemaJSON, err := json.Marshal(schema)
	if err != nil {
		return fmt.Errorf("marshaling schema: %w", err)
	}

	// Verify artifact exists
	_, err = s.repo.GetArtifactByGroupAndId(ctx, db.GetArtifactByGroupAndIdParams{
		GroupID:    groupID,
		ArtifactID: artifactID,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrArtifactNotFound
		}
		return fmt.Errorf("checking artifact: %w", err)
	}

	return s.repo.WithTx(ctx, func(tx repository.Tx) error {
		return tx.UpdateArtifactVersionSchema(ctx, db.UpdateArtifactVersionSchemaParams{
			GroupID:       groupID,
			ArtifactID:    artifactID,
			VersionSchema: schemaJSON,
		})
	})
}

// GetVersionSchema returns the version schema for an artifact.
func (s *Service) GetVersionSchema(ctx context.Context, groupID, artifactID string) (*domain.VersionSchema, error) {
	schemaBytes, err := s.repo.GetArtifactVersionSchema(ctx, db.GetArtifactVersionSchemaParams{
		GroupID:    groupID,
		ArtifactID: artifactID,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrArtifactNotFound
		}
		return nil, fmt.Errorf("getting schema: %w", err)
	}
	if schemaBytes == nil {
		return nil, ErrSchemaNotFound
	}

	var schema domain.VersionSchema
	if err := json.Unmarshal(schemaBytes, &schema); err != nil {
		return nil, fmt.Errorf("unmarshaling schema: %w", err)
	}
	return &schema, nil
}

// UpdateArtifactInput holds optional fields for updating an artifact.
type UpdateArtifactInput struct {
	DisplayName     *string
	Website         *string
	Issues          *string
	GitRepositories []string // nil means don't change
}

// UpdateArtifact updates mutable fields on an artifact. Only non-nil fields are changed.
func (s *Service) UpdateArtifact(ctx context.Context, groupID, artifactID string, input UpdateArtifactInput) (*domain.Artifact, error) {
	var gitReposJSON []byte
	if input.GitRepositories != nil {
		var err error
		gitReposJSON, err = json.Marshal(input.GitRepositories)
		if err != nil {
			return nil, fmt.Errorf("marshaling git repositories: %w", err)
		}
	}

	var updated db.Artifact
	err := s.repo.WithTx(ctx, func(tx repository.Tx) error {
		var err error
		updated, err = tx.UpdateArtifactFields(ctx, db.UpdateArtifactFieldsParams{
			GroupID:         groupID,
			ArtifactID:      artifactID,
			Name:            input.DisplayName,
			Website:         input.Website,
			Issues:          input.Issues,
			GitRepositories: gitReposJSON,
		})
		return err
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrArtifactNotFound
		}
		return nil, fmt.Errorf("updating artifact: %w", err)
	}

	var gitRepos []string
	if err := json.Unmarshal(updated.GitRepositories, &gitRepos); err != nil {
		return nil, fmt.Errorf("unmarshaling git repos: %w", err)
	}

	return &domain.Artifact{
		GroupID:         updated.GroupID,
		ArtifactID:      updated.ArtifactID,
		DisplayName:     updated.Name,
		Website:         updated.Website,
		Issues:          updated.Issues,
		GitRepositories: gitRepos,
	}, nil
}

func deref(p *string) string {
	if p == nil {
		return ""
	}
	return *p
}
