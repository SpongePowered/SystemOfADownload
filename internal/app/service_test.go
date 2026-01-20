package app_test

import (
	"context"
	"errors"
	"testing"

	"github.com/google/go-cmp/cmp"
	"github.com/jackc/pgx/v5"
	"github.com/spongepowered/systemofadownload/internal/app"
	appmocks "github.com/spongepowered/systemofadownload/internal/app/mocks"
	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/stretchr/testify/mock"
)

func TestService_GetGroup(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name      string
		groupID   string
		mockSetup func(m *appmocks.MockRepository)
		want      *domain.Group
		wantErr   error
	}{
		{
			name:    "found",
			groupID: "com.example",
			mockSetup: func(m *appmocks.MockRepository) {
				m.EXPECT().GetGroup(mock.Anything, "com.example").Return(db.Group{
					MavenID: "com.example",
					Name:    "Example",
					Website: strPtr("https://example.com"),
				}, nil)
			},
			want: &domain.Group{
				GroupID: "com.example",
				Name:    "Example",
				Website: strPtr("https://example.com"),
			},
		},
		{
			name:    "not found",
			groupID: "missing",
			mockSetup: func(m *appmocks.MockRepository) {
				m.EXPECT().GetGroup(mock.Anything, "missing").Return(db.Group{}, pgx.ErrNoRows)
			},
			want: nil,
		},
		{
			name:    "db error",
			groupID: "boom",
			mockSetup: func(m *appmocks.MockRepository) {
				m.EXPECT().GetGroup(mock.Anything, "boom").Return(db.Group{}, errors.New("db failure"))
			},
			wantErr: errors.New("db failure"),
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			mockRepo := appmocks.NewMockRepository(t)
			if tt.mockSetup != nil {
				tt.mockSetup(mockRepo)
			}

			svc := app.NewService(mockRepo)
			got, err := svc.GetGroup(context.Background(), tt.groupID)

			if tt.wantErr != nil {
				if err == nil {
					t.Fatalf("expected error %v, got nil", tt.wantErr)
				}
				if err.Error() != tt.wantErr.Error() {
					t.Fatalf("expected error %v, got %v", tt.wantErr, err)
				}
				return
			}

			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}

			if diff := cmp.Diff(tt.want, got); diff != "" {
				t.Fatalf("group diff (-want +got):\n%s", diff)
			}
		})
	}
}

func TestService_ListGroups(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name      string
		mockSetup func(m *appmocks.MockRepository)
		want      []*domain.Group
		wantErr   error
	}{
		{
			name: "ok",
			mockSetup: func(m *appmocks.MockRepository) {
				m.EXPECT().ListGroups(mock.Anything).Return([]db.Group{
					{MavenID: "g1", Name: "Group 1"},
					{MavenID: "g2", Name: "Group 2", Website: strPtr("https://g2.example")},
				}, nil)
			},
			want: []*domain.Group{
				{GroupID: "g1", Name: "Group 1"},
				{GroupID: "g2", Name: "Group 2", Website: strPtr("https://g2.example")},
			},
		},
		{
			name: "db error",
			mockSetup: func(m *appmocks.MockRepository) {
				m.EXPECT().ListGroups(mock.Anything).Return(nil, errors.New("boom"))
			},
			wantErr: errors.New("boom"),
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			mockRepo := appmocks.NewMockRepository(t)
			if tt.mockSetup != nil {
				tt.mockSetup(mockRepo)
			}

			svc := app.NewService(mockRepo)
			got, err := svc.ListGroups(context.Background())

			if tt.wantErr != nil {
				if err == nil {
					t.Fatalf("expected error %v, got nil", tt.wantErr)
				}
				if err.Error() != tt.wantErr.Error() {
					t.Fatalf("expected error %v, got %v", tt.wantErr, err)
				}
				return
			}

			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}

			if diff := cmp.Diff(tt.want, got); diff != "" {
				t.Fatalf("groups diff (-want +got):\n%s", diff)
			}
		})
	}
}

func TestService_RegisterGroup(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name      string
		input     *domain.Group
		mockSetup func(m *appmocks.MockRepository)
		wantErr   error
	}{
		{
			name:  "ok - new group",
			input: &domain.Group{GroupID: "g1", Name: "Group", Website: strPtr("https://g1")},
			mockSetup: func(m *appmocks.MockRepository) {
				m.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(app.Repository) error) error {
						// Create a new mock for the transaction
						txRepo := appmocks.NewMockRepository(t)
						txRepo.EXPECT().CreateGroup(mock.Anything, db.CreateGroupParams{
							MavenID: "g1",
							Name:    "Group",
							Website: strPtr("https://g1"),
						}).Return(db.Group{}, nil)
						return fn(txRepo)
					},
				)
			},
		},
		{
			name:  "ok - group already exists (idempotent)",
			input: &domain.Group{GroupID: "existing.group", Name: "Existing Group"},
			mockSetup: func(m *appmocks.MockRepository) {
				m.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(app.Repository) error) error {
						// CreateGroup with ON CONFLICT should not error, just update
						txRepo := appmocks.NewMockRepository(t)
						txRepo.EXPECT().CreateGroup(mock.Anything, db.CreateGroupParams{
							MavenID: "existing.group",
							Name:    "Existing Group",
							Website: nil,
						}).Return(db.Group{}, nil)
						return fn(txRepo)
					},
				)
			},
		},
		{
			name:  "db error on create",
			input: &domain.Group{GroupID: "g2", Name: "Group 2"},
			mockSetup: func(m *appmocks.MockRepository) {
				m.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(app.Repository) error) error {
						txRepo := appmocks.NewMockRepository(t)
						txRepo.EXPECT().CreateGroup(mock.Anything, db.CreateGroupParams{
							MavenID: "g2",
							Name:    "Group 2",
							Website: nil,
						}).Return(db.Group{}, errors.New("insert failed"))
						return fn(txRepo)
					},
				)
			},
			wantErr: errors.New("insert failed"),
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			mockRepo := appmocks.NewMockRepository(t)
			if tt.mockSetup != nil {
				tt.mockSetup(mockRepo)
			}

			svc := app.NewService(mockRepo)
			err := svc.RegisterGroup(context.Background(), tt.input)

			if tt.wantErr != nil {
				if err == nil {
					t.Fatalf("expected error %v, got nil", tt.wantErr)
				}
				if err.Error() != tt.wantErr.Error() {
					t.Fatalf("expected error %v, got %v", tt.wantErr, err)
				}
				return
			}

			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
		})
	}
}

func strPtr(s string) *string {
	return &s
}
