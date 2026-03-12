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
)

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
			return nil, nil
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
	// Use a transaction to ensure atomicity of the check-then-insert operation.
	return s.repo.WithTx(ctx, func(tx repository.Tx) error {
		// Check if group already exists (case insensitive)
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

func (s *Service) RegisterArtifact(ctx context.Context, artifact *domain.Artifact) error {
	// Use a transaction to ensure atomicity
	return s.repo.WithTx(ctx, func(tx repository.Tx) error {
		// Check if the group exists
		_, err := tx.GetGroup(ctx, artifact.GroupID)
		if err != nil {
			if errors.Is(err, pgx.ErrNoRows) {
				return ErrGroupNotFound
			}
			return fmt.Errorf("failed to check if group exists: %w", err)
		}

		// Check if artifact already exists
		_, err = tx.GetArtifactByGroupAndId(ctx, db.GetArtifactByGroupAndIdParams{
			GroupID:    artifact.GroupID,
			ArtifactID: artifact.ArtifactID,
		})
		if err == nil {
			// Artifact exists
			return ErrArtifactAlreadyExists
		}
		if !errors.Is(err, pgx.ErrNoRows) {
			return fmt.Errorf("failed to check if artifact exists: %w", err)
		}

		// Create git repositories JSON array
		gitRepos := artifact.GitRepositories
		gitReposJSON, err := json.Marshal(gitRepos)
		if err != nil {
			return fmt.Errorf("failed to marshal git repositories: %w", err)
		}

		// Create the artifact
		_, err = tx.CreateArtifact(ctx, db.CreateArtifactParams{
			GroupID:         artifact.GroupID,
			ArtifactID:      artifact.ArtifactID,
			Name:            artifact.DisplayName,
			Website:         artifact.Website,
			GitRepositories: gitReposJSON,
		})
		return err
	})
}
