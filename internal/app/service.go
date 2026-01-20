package app

import (
	"context"
	"errors"
	"fmt"

	"github.com/jackc/pgx/v5"
	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/repository"
)

var (
	ErrGroupAlreadyExists = errors.New("group already exists")
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
	return s.repo.WithTx(ctx, func(q db.Querier) error {
		// Check if group already exists (case insensitive)
		exists, err := q.GroupExistsByMavenID(ctx, group.GroupID)
		if err != nil {
			return fmt.Errorf("failed to check if group exists: %w", err)
		}
		if exists {
			return ErrGroupAlreadyExists
		}

		_, err = q.CreateGroup(ctx, db.CreateGroupParams{
			MavenID: group.GroupID,
			Name:    group.Name,
			Website: group.Website,
		})
		return err
	})
}
