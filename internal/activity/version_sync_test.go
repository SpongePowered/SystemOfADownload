package activity_test

import (
	"context"
	"errors"
	"testing"

	"github.com/google/go-cmp/cmp"
	"github.com/stretchr/testify/mock"

	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/repository"
	repomocks "github.com/spongepowered/systemofadownload/internal/repository/mocks"
	sonatypemocks "github.com/spongepowered/systemofadownload/internal/sonatype/mocks"
)

func TestFetchVersions(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name      string
		input     activity.FetchVersionsInput
		mockSetup func(t *testing.T, m *sonatypemocks.MockClient)
		want      *activity.FetchVersionsOutput
		wantErr   bool
	}{
		{
			name: "successful fetch returns versions",
			input: activity.FetchVersionsInput{
				GroupID:    "org.spongepowered",
				ArtifactID: "spongeapi",
			},
			mockSetup: func(t *testing.T, m *sonatypemocks.MockClient) {
				m.EXPECT().FetchVersions(
					t.Context(),
					"org.spongepowered",
					"spongeapi",
				).Return([]domain.VersionInfo{
					{GroupID: "org.spongepowered", ArtifactID: "spongeapi", Version: "8.0.0"},
					{GroupID: "org.spongepowered", ArtifactID: "spongeapi", Version: "7.4.0"},
				}, nil)
			},
			want: &activity.FetchVersionsOutput{
				Versions: []domain.VersionInfo{
					{GroupID: "org.spongepowered", ArtifactID: "spongeapi", Version: "8.0.0"},
					{GroupID: "org.spongepowered", ArtifactID: "spongeapi", Version: "7.4.0"},
				},
			},
		},
		{
			name: "API error propagates",
			input: activity.FetchVersionsInput{
				GroupID:    "org.spongepowered",
				ArtifactID: "spongeapi",
			},
			mockSetup: func(t *testing.T, m *sonatypemocks.MockClient) {
				m.EXPECT().FetchVersions(
					t.Context(),
					"org.spongepowered",
					"spongeapi",
				).Return(nil, errors.New("connection refused"))
			},
			wantErr: true,
		},
		{
			name: "empty versions list",
			input: activity.FetchVersionsInput{
				GroupID:    "org.nonexistent",
				ArtifactID: "nothing",
			},
			mockSetup: func(t *testing.T, m *sonatypemocks.MockClient) {
				m.EXPECT().FetchVersions(
					t.Context(),
					"org.nonexistent",
					"nothing",
				).Return([]domain.VersionInfo{}, nil)
			},
			want: &activity.FetchVersionsOutput{
				Versions: []domain.VersionInfo{},
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			mockClient := sonatypemocks.NewMockClient(t)
			if tt.mockSetup != nil {
				tt.mockSetup(t, mockClient)
			}

			a := &activity.VersionSyncActivities{SonatypeClient: mockClient}
			got, err := a.FetchVersions(t.Context(), tt.input)

			if tt.wantErr {
				if err == nil {
					t.Fatal("expected error, got nil")
				}
				return
			}

			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}

			if diff := cmp.Diff(tt.want, got); diff != "" {
				t.Fatalf("output diff (-want +got):\n%s", diff)
			}
		})
	}
}

func TestStoreNewVersions(t *testing.T) {
	t.Parallel()

	const (
		groupID    = "org.spongepowered"
		artifactID = "spongeapi"
	)
	artifactRow := db.Artifact{ID: 42, GroupID: groupID, ArtifactID: artifactID, Name: "SpongeAPI"}

	tests := []struct {
		name      string
		input     activity.StoreNewVersionsInput
		mockSetup func(t *testing.T, repo *repomocks.MockRepository, tx *repomocks.MockTx)
		want      *activity.StoreNewVersionsOutput
		wantErr   bool
	}{
		{
			name: "empty input returns immediately",
			input: activity.StoreNewVersionsInput{
				GroupID:    groupID,
				ArtifactID: artifactID,
				Versions:   nil,
			},
			mockSetup: func(t *testing.T, repo *repomocks.MockRepository, tx *repomocks.MockTx) {},
			want:      &activity.StoreNewVersionsOutput{},
		},
		{
			name: "all versions are new",
			input: activity.StoreNewVersionsInput{
				GroupID:    groupID,
				ArtifactID: artifactID,
				Versions: []domain.VersionInfo{
					{GroupID: groupID, ArtifactID: artifactID, Version: "8.0.0"},
					{GroupID: groupID, ArtifactID: artifactID, Version: "7.4.0"},
				},
			},
			mockSetup: func(t *testing.T, repo *repomocks.MockRepository, tx *repomocks.MockTx) {
				repo.EXPECT().GetArtifactByGroupAndId(mock.Anything, db.GetArtifactByGroupAndIdParams{
					GroupID: groupID, ArtifactID: artifactID,
				}).Return(artifactRow, nil)

				repo.EXPECT().ListArtifactVersionStringsByArtifactID(mock.Anything, db.ListArtifactVersionStringsByArtifactIDParams{
					ArtifactID: 42, Limit: 100, Offset: 0,
				}).Return([]string{}, nil)

				tx.EXPECT().CreateArtifactVersion(mock.Anything, db.CreateArtifactVersionParams{
					ArtifactID: 42, Version: "8.0.0",
				}).Return(db.ArtifactVersion{ID: 1, ArtifactID: 42, Version: "8.0.0"}, nil)

				tx.EXPECT().CreateArtifactVersion(mock.Anything, db.CreateArtifactVersionParams{
					ArtifactID: 42, Version: "7.4.0",
				}).Return(db.ArtifactVersion{ID: 2, ArtifactID: 42, Version: "7.4.0"}, nil)

				repo.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(repository.Tx) error) error {
						return fn(tx)
					},
				)
			},
			want: &activity.StoreNewVersionsOutput{
				NewVersions: []domain.VersionInfo{
					{GroupID: groupID, ArtifactID: artifactID, Version: "8.0.0"},
					{GroupID: groupID, ArtifactID: artifactID, Version: "7.4.0"},
				},
			},
		},
		{
			name: "filters out existing versions",
			input: activity.StoreNewVersionsInput{
				GroupID:    groupID,
				ArtifactID: artifactID,
				Versions: []domain.VersionInfo{
					{GroupID: groupID, ArtifactID: artifactID, Version: "8.0.0"},
					{GroupID: groupID, ArtifactID: artifactID, Version: "7.4.0"},
					{GroupID: groupID, ArtifactID: artifactID, Version: "7.3.0"},
				},
			},
			mockSetup: func(t *testing.T, repo *repomocks.MockRepository, tx *repomocks.MockTx) {
				repo.EXPECT().GetArtifactByGroupAndId(mock.Anything, db.GetArtifactByGroupAndIdParams{
					GroupID: groupID, ArtifactID: artifactID,
				}).Return(artifactRow, nil)

				repo.EXPECT().ListArtifactVersionStringsByArtifactID(mock.Anything, db.ListArtifactVersionStringsByArtifactIDParams{
					ArtifactID: 42, Limit: 100, Offset: 0,
				}).Return([]string{"7.3.0", "7.4.0"}, nil)

				tx.EXPECT().CreateArtifactVersion(mock.Anything, db.CreateArtifactVersionParams{
					ArtifactID: 42, Version: "8.0.0",
				}).Return(db.ArtifactVersion{ID: 1, ArtifactID: 42, Version: "8.0.0"}, nil)

				repo.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(repository.Tx) error) error {
						return fn(tx)
					},
				)
			},
			want: &activity.StoreNewVersionsOutput{
				NewVersions: []domain.VersionInfo{
					{GroupID: groupID, ArtifactID: artifactID, Version: "8.0.0"},
				},
			},
		},
		{
			name: "all versions already exist",
			input: activity.StoreNewVersionsInput{
				GroupID:    groupID,
				ArtifactID: artifactID,
				Versions: []domain.VersionInfo{
					{GroupID: groupID, ArtifactID: artifactID, Version: "7.4.0"},
				},
			},
			mockSetup: func(t *testing.T, repo *repomocks.MockRepository, tx *repomocks.MockTx) {
				repo.EXPECT().GetArtifactByGroupAndId(mock.Anything, db.GetArtifactByGroupAndIdParams{
					GroupID: groupID, ArtifactID: artifactID,
				}).Return(artifactRow, nil)

				repo.EXPECT().ListArtifactVersionStringsByArtifactID(mock.Anything, db.ListArtifactVersionStringsByArtifactIDParams{
					ArtifactID: 42, Limit: 100, Offset: 0,
				}).Return([]string{"7.4.0"}, nil)
			},
			want: &activity.StoreNewVersionsOutput{},
		},
		{
			name: "artifact lookup error propagates",
			input: activity.StoreNewVersionsInput{
				GroupID:    groupID,
				ArtifactID: artifactID,
				Versions: []domain.VersionInfo{
					{GroupID: groupID, ArtifactID: artifactID, Version: "8.0.0"},
				},
			},
			mockSetup: func(t *testing.T, repo *repomocks.MockRepository, tx *repomocks.MockTx) {
				repo.EXPECT().GetArtifactByGroupAndId(mock.Anything, db.GetArtifactByGroupAndIdParams{
					GroupID: groupID, ArtifactID: artifactID,
				}).Return(db.Artifact{}, errors.New("no rows"))
			},
			wantErr: true,
		},
		{
			name: "transaction error propagates",
			input: activity.StoreNewVersionsInput{
				GroupID:    groupID,
				ArtifactID: artifactID,
				Versions: []domain.VersionInfo{
					{GroupID: groupID, ArtifactID: artifactID, Version: "8.0.0"},
				},
			},
			mockSetup: func(t *testing.T, repo *repomocks.MockRepository, tx *repomocks.MockTx) {
				repo.EXPECT().GetArtifactByGroupAndId(mock.Anything, db.GetArtifactByGroupAndIdParams{
					GroupID: groupID, ArtifactID: artifactID,
				}).Return(artifactRow, nil)

				repo.EXPECT().ListArtifactVersionStringsByArtifactID(mock.Anything, db.ListArtifactVersionStringsByArtifactIDParams{
					ArtifactID: 42, Limit: 100, Offset: 0,
				}).Return([]string{}, nil)

				tx.EXPECT().CreateArtifactVersion(mock.Anything, db.CreateArtifactVersionParams{
					ArtifactID: 42, Version: "8.0.0",
				}).Return(db.ArtifactVersion{}, errors.New("unique violation"))

				repo.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(repository.Tx) error) error {
						return fn(tx)
					},
				)
			},
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			mockRepo := repomocks.NewMockRepository(t)
			mockTx := repomocks.NewMockTx(t)
			if tt.mockSetup != nil {
				tt.mockSetup(t, mockRepo, mockTx)
			}

			a := &activity.VersionSyncActivities{Repo: mockRepo}
			got, err := a.StoreNewVersions(t.Context(), tt.input)

			if tt.wantErr {
				if err == nil {
					t.Fatal("expected error, got nil")
				}
				return
			}

			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}

			if diff := cmp.Diff(tt.want, got); diff != "" {
				t.Fatalf("output diff (-want +got):\n%s", diff)
			}
		})
	}
}
