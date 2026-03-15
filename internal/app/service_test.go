package app_test

import (
	"context"
	"errors"
	"testing"

	"github.com/google/go-cmp/cmp"
	"github.com/jackc/pgx/v5"
	"github.com/spongepowered/systemofadownload/internal/app"
	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/repository"
	repositorymocks "github.com/spongepowered/systemofadownload/internal/repository/mocks"
	"github.com/stretchr/testify/mock"
)

func TestService_GetGroup(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name      string
		groupID   string
		mockSetup func(t *testing.T, m *repositorymocks.MockRepository)
		want      *domain.Group
		wantErr   error
	}{
		{
			name:    "found",
			groupID: "com.example",
			mockSetup: func(t *testing.T, m *repositorymocks.MockRepository) {
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
			mockSetup: func(t *testing.T, m *repositorymocks.MockRepository) {
				m.EXPECT().GetGroup(mock.Anything, "missing").Return(db.Group{}, pgx.ErrNoRows)
			},
			wantErr: app.ErrGroupNotFound,
		},
		{
			name:    "db error",
			groupID: "boom",
			mockSetup: func(t *testing.T, m *repositorymocks.MockRepository) {
				m.EXPECT().GetGroup(mock.Anything, "boom").Return(db.Group{}, errors.New("db failure"))
			},
			wantErr: errors.New("db failure"),
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			mockRepo := repositorymocks.NewMockRepository(t)
			if tt.mockSetup != nil {
				tt.mockSetup(t, mockRepo)
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
		mockSetup func(t *testing.T, m *repositorymocks.MockRepository)
		want      []*domain.Group
		wantErr   error
	}{
		{
			name: "ok",
			mockSetup: func(t *testing.T, m *repositorymocks.MockRepository) {
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
			mockSetup: func(t *testing.T, m *repositorymocks.MockRepository) {
				m.EXPECT().ListGroups(mock.Anything).Return(nil, errors.New("boom"))
			},
			wantErr: errors.New("boom"),
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			mockRepo := repositorymocks.NewMockRepository(t)
			if tt.mockSetup != nil {
				tt.mockSetup(t, mockRepo)
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
		mockSetup func(t *testing.T, m *repositorymocks.MockRepository)
		wantErr   error
	}{
		{
			name:  "ok - new group",
			input: &domain.Group{GroupID: "g1", Name: "Group", Website: strPtr("https://g1")},
			mockSetup: func(t *testing.T, m *repositorymocks.MockRepository) {
				m.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(repository.Tx) error) error {
						// Create a new mock for the transaction
						txMock := repositorymocks.NewMockTx(t)
						txMock.EXPECT().GroupExistsByMavenID(mock.Anything, "g1").Return(false, nil)
						txMock.EXPECT().CreateGroup(mock.Anything, db.CreateGroupParams{
							MavenID: "g1",
							Name:    "Group",
							Website: strPtr("https://g1"),
						}).Return(db.Group{}, nil)
						return fn(txMock)
					},
				)
			},
		},
		{
			name:  "group already exists - same case",
			input: &domain.Group{GroupID: "existing.group", Name: "Existing Group"},
			mockSetup: func(t *testing.T, m *repositorymocks.MockRepository) {
				m.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(repository.Tx) error) error {
						txMock := repositorymocks.NewMockTx(t)
						txMock.EXPECT().GroupExistsByMavenID(mock.Anything, "existing.group").Return(true, nil)
						return fn(txMock)
					},
				)
			},
			wantErr: app.ErrGroupAlreadyExists,
		},
		{
			name:  "group already exists - different case",
			input: &domain.Group{GroupID: "EXISTING.GROUP", Name: "Existing Group"},
			mockSetup: func(t *testing.T, m *repositorymocks.MockRepository) {
				m.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(repository.Tx) error) error {
						txMock := repositorymocks.NewMockTx(t)
						txMock.EXPECT().GroupExistsByMavenID(mock.Anything, "EXISTING.GROUP").Return(true, nil)
						return fn(txMock)
					},
				)
			},
			wantErr: app.ErrGroupAlreadyExists,
		},
		{
			name:  "error checking existence",
			input: &domain.Group{GroupID: "g3", Name: "Group 3"},
			mockSetup: func(t *testing.T, m *repositorymocks.MockRepository) {
				m.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(repository.Tx) error) error {
						txMock := repositorymocks.NewMockTx(t)
						txMock.EXPECT().GroupExistsByMavenID(mock.Anything, "g3").Return(false, errors.New("db check failed"))
						return fn(txMock)
					},
				)
			},
			wantErr: errors.New("failed to check if group exists: db check failed"),
		},
		{
			name:  "db error on create",
			input: &domain.Group{GroupID: "g2", Name: "Group 2"},
			mockSetup: func(t *testing.T, m *repositorymocks.MockRepository) {
				m.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(repository.Tx) error) error {
						txMock := repositorymocks.NewMockTx(t)
						txMock.EXPECT().GroupExistsByMavenID(mock.Anything, "g2").Return(false, nil)
						txMock.EXPECT().CreateGroup(mock.Anything, db.CreateGroupParams{
							MavenID: "g2",
							Name:    "Group 2",
							Website: nil,
						}).Return(db.Group{}, errors.New("insert failed"))
						return fn(txMock)
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

			mockRepo := repositorymocks.NewMockRepository(t)
			if tt.mockSetup != nil {
				tt.mockSetup(t, mockRepo)
			}

			svc := app.NewService(mockRepo)
			err := svc.RegisterGroup(context.Background(), tt.input)

			if tt.wantErr != nil {
				if err == nil {
					t.Fatalf("expected error %v, got nil", tt.wantErr)
				}
				// Use errors.Is for sentinel errors, otherwise compare messages
				if errors.Is(tt.wantErr, app.ErrGroupAlreadyExists) {
					if !errors.Is(err, app.ErrGroupAlreadyExists) {
						t.Fatalf("expected error %v, got %v", tt.wantErr, err)
					}
				} else if err.Error() != tt.wantErr.Error() {
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

func TestService_RegisterArtifact(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name      string
		input     *domain.Artifact
		mockSetup func(t *testing.T, m *repositorymocks.MockRepository)
		wantErr   error
	}{
		{
			name: "ok - new artifact",
			input: &domain.Artifact{
				GroupID:         "org.example",
				ArtifactID:      "myartifact",
				DisplayName:     "My Artifact",
				GitRepositories: []string{"https://github.com/example/myartifact"},
				Website:         strPtr("https://example.org"),
				Issues:          strPtr("https://github.com/example/myartifact/issues"),
			},
			mockSetup: func(t *testing.T, m *repositorymocks.MockRepository) {
				m.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(repository.Tx) error) error {
						txMock := repositorymocks.NewMockTx(t)
						// Group exists
						txMock.EXPECT().GetGroup(mock.Anything, "org.example").Return(db.Group{
							MavenID: "org.example",
							Name:    "Example",
						}, nil)
						// Artifact doesn't exist
						txMock.EXPECT().GetArtifactByGroupAndId(mock.Anything, db.GetArtifactByGroupAndIdParams{
							GroupID:    "org.example",
							ArtifactID: "myartifact",
						}).Return(db.Artifact{}, pgx.ErrNoRows)
						// Create artifact
						txMock.EXPECT().CreateArtifact(mock.Anything, mock.MatchedBy(func(params db.CreateArtifactParams) bool {
							return params.GroupID == "org.example" &&
								params.ArtifactID == "myartifact" &&
								params.Name == "My Artifact" &&
								params.Website != nil && *params.Website == "https://example.org"
						})).Return(db.Artifact{}, nil)
						return fn(txMock)
					},
				)
			},
		},
		{
			name: "ok - artifact with multiple git repositories",
			input: &domain.Artifact{
				GroupID:     "org.example",
				ArtifactID:  "multirepo",
				DisplayName: "Multi Repo Artifact",
				GitRepositories: []string{
					"https://github.com/example/repo1",
					"https://github.com/example/repo2",
				},
			},
			mockSetup: func(t *testing.T, m *repositorymocks.MockRepository) {
				m.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(repository.Tx) error) error {
						txMock := repositorymocks.NewMockTx(t)
						txMock.EXPECT().GetGroup(mock.Anything, "org.example").Return(db.Group{
							MavenID: "org.example",
							Name:    "Example",
						}, nil)
						txMock.EXPECT().GetArtifactByGroupAndId(mock.Anything, db.GetArtifactByGroupAndIdParams{
							GroupID:    "org.example",
							ArtifactID: "multirepo",
						}).Return(db.Artifact{}, pgx.ErrNoRows)
						txMock.EXPECT().CreateArtifact(mock.Anything, mock.MatchedBy(func(params db.CreateArtifactParams) bool {
							return params.GroupID == "org.example" &&
								params.ArtifactID == "multirepo" &&
								params.Name == "Multi Repo Artifact"
						})).Return(db.Artifact{}, nil)
						return fn(txMock)
					},
				)
			},
		},
		{
			name: "group not found",
			input: &domain.Artifact{
				GroupID:         "org.nonexistent",
				ArtifactID:      "artifact",
				DisplayName:     "Artifact",
				GitRepositories: []string{"https://github.com/example/artifact"},
			},
			mockSetup: func(t *testing.T, m *repositorymocks.MockRepository) {
				m.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(repository.Tx) error) error {
						txMock := repositorymocks.NewMockTx(t)
						txMock.EXPECT().GetGroup(mock.Anything, "org.nonexistent").Return(db.Group{}, pgx.ErrNoRows)
						return fn(txMock)
					},
				)
			},
			wantErr: app.ErrGroupNotFound,
		},
		{
			name: "artifact already exists",
			input: &domain.Artifact{
				GroupID:         "org.example",
				ArtifactID:      "existing",
				DisplayName:     "Existing Artifact",
				GitRepositories: []string{"https://github.com/example/existing"},
			},
			mockSetup: func(t *testing.T, m *repositorymocks.MockRepository) {
				m.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(repository.Tx) error) error {
						txMock := repositorymocks.NewMockTx(t)
						txMock.EXPECT().GetGroup(mock.Anything, "org.example").Return(db.Group{
							MavenID: "org.example",
							Name:    "Example",
						}, nil)
						txMock.EXPECT().GetArtifactByGroupAndId(mock.Anything, db.GetArtifactByGroupAndIdParams{
							GroupID:    "org.example",
							ArtifactID: "existing",
						}).Return(db.Artifact{
							ID:         123,
							GroupID:    "org.example",
							ArtifactID: "existing",
						}, nil)
						return fn(txMock)
					},
				)
			},
			wantErr: app.ErrArtifactAlreadyExists,
		},
		{
			name: "error checking if group exists",
			input: &domain.Artifact{
				GroupID:         "org.example",
				ArtifactID:      "artifact",
				DisplayName:     "Artifact",
				GitRepositories: []string{"https://github.com/example/artifact"},
			},
			mockSetup: func(t *testing.T, m *repositorymocks.MockRepository) {
				m.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(repository.Tx) error) error {
						txMock := repositorymocks.NewMockTx(t)
						txMock.EXPECT().GetGroup(mock.Anything, "org.example").Return(db.Group{}, errors.New("db connection failed"))
						return fn(txMock)
					},
				)
			},
			wantErr: errors.New("failed to check if group exists: db connection failed"),
		},
		{
			name: "error checking if artifact exists",
			input: &domain.Artifact{
				GroupID:         "org.example",
				ArtifactID:      "artifact",
				DisplayName:     "Artifact",
				GitRepositories: []string{"https://github.com/example/artifact"},
			},
			mockSetup: func(t *testing.T, m *repositorymocks.MockRepository) {
				m.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(repository.Tx) error) error {
						txMock := repositorymocks.NewMockTx(t)
						txMock.EXPECT().GetGroup(mock.Anything, "org.example").Return(db.Group{
							MavenID: "org.example",
							Name:    "Example",
						}, nil)
						txMock.EXPECT().GetArtifactByGroupAndId(mock.Anything, db.GetArtifactByGroupAndIdParams{
							GroupID:    "org.example",
							ArtifactID: "artifact",
						}).Return(db.Artifact{}, errors.New("db query failed"))
						return fn(txMock)
					},
				)
			},
			wantErr: errors.New("failed to check if artifact exists: db query failed"),
		},
		{
			name: "error creating artifact",
			input: &domain.Artifact{
				GroupID:         "org.example",
				ArtifactID:      "artifact",
				DisplayName:     "Artifact",
				GitRepositories: []string{"https://github.com/example/artifact"},
			},
			mockSetup: func(t *testing.T, m *repositorymocks.MockRepository) {
				m.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(repository.Tx) error) error {
						txMock := repositorymocks.NewMockTx(t)
						txMock.EXPECT().GetGroup(mock.Anything, "org.example").Return(db.Group{
							MavenID: "org.example",
							Name:    "Example",
						}, nil)
						txMock.EXPECT().GetArtifactByGroupAndId(mock.Anything, db.GetArtifactByGroupAndIdParams{
							GroupID:    "org.example",
							ArtifactID: "artifact",
						}).Return(db.Artifact{}, pgx.ErrNoRows)
						txMock.EXPECT().CreateArtifact(mock.Anything, mock.Anything).Return(db.Artifact{}, errors.New("insert failed"))
						return fn(txMock)
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

			mockRepo := repositorymocks.NewMockRepository(t)
			if tt.mockSetup != nil {
				tt.mockSetup(t, mockRepo)
			}

			svc := app.NewService(mockRepo)
			err := svc.RegisterArtifact(context.Background(), tt.input)

			if tt.wantErr != nil {
				if err == nil {
					t.Fatalf("expected error %v, got nil", tt.wantErr)
				}
				// Use errors.Is for sentinel errors, otherwise compare messages
				if errors.Is(tt.wantErr, app.ErrGroupNotFound) {
					if !errors.Is(err, app.ErrGroupNotFound) {
						t.Fatalf("expected error %v, got %v", tt.wantErr, err)
					}
				} else if errors.Is(tt.wantErr, app.ErrArtifactAlreadyExists) {
					if !errors.Is(err, app.ErrArtifactAlreadyExists) {
						t.Fatalf("expected error %v, got %v", tt.wantErr, err)
					}
				} else if err.Error() != tt.wantErr.Error() {
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
