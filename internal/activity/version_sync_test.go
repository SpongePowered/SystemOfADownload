package activity_test

import (
	"context"
	"errors"
	"testing"

	"github.com/google/go-cmp/cmp"
	"github.com/stretchr/testify/mock"
	"go.temporal.io/sdk/temporal"

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

func TestFetchVersionsUnknownSource(t *testing.T) {
	t.Parallel()

	a := &activity.VersionSyncActivities{SonatypeClient: sonatypemocks.NewMockClient(t)}
	env := newActivityEnv(t)
	env.RegisterActivity(a.FetchVersions)

	_, err := env.ExecuteActivity(a.FetchVersions, activity.FetchVersionsInput{
		GroupID:    "g",
		ArtifactID: "a",
		Source:     "nonsense",
	})
	if err == nil {
		t.Fatal("expected error on unknown source")
	}
	var appErr *temporal.ApplicationError
	if !errors.As(err, &appErr) || !appErr.NonRetryable() {
		t.Fatalf("expected non-retryable ApplicationError, got %T: %v", err, err)
	}
}

// TestFetchVersionsMetadataGating verifies the metadata path drops candidates
// whose assets aren't yet published. This is the fix for the ghost-row bug —
// a candidate maven-metadata entry without a corresponding asset must not be
// returned to StoreNewVersions.
func TestFetchVersionsMetadataGating(t *testing.T) {
	t.Parallel()

	const groupID, artifactID = "org.spongepowered", "spongeforge"
	artifactRow := db.Artifact{ID: 7, GroupID: groupID, ArtifactID: artifactID, Name: "SpongeForge"}

	tests := []struct {
		name         string
		candidates   []domain.VersionInfo
		existingInDB []string
		hasAssets    map[string]bool
		wantAccepted []domain.VersionInfo
	}{
		{
			// Steady-state: DB has prior version 0.9.0, metadata lists 0.9.0
			// plus two new ones with assets. Both new are probed and accepted.
			name: "all new candidates have assets",
			candidates: []domain.VersionInfo{
				{GroupID: groupID, ArtifactID: artifactID, Version: "0.9.0"},
				{GroupID: groupID, ArtifactID: artifactID, Version: "1.0.0"},
				{GroupID: groupID, ArtifactID: artifactID, Version: "2.0.0"},
			},
			existingInDB: []string{"0.9.0"},
			hasAssets:    map[string]bool{"1.0.0": true, "2.0.0": true},
			wantAccepted: []domain.VersionInfo{
				{GroupID: groupID, ArtifactID: artifactID, Version: "0.9.0"},
				{GroupID: groupID, ArtifactID: artifactID, Version: "1.0.0"},
				{GroupID: groupID, ArtifactID: artifactID, Version: "2.0.0"},
			},
		},
		{
			// Ghost-row fix: 2.0.0 is listed in maven-metadata but assets
			// aren't indexed yet (published seconds ago). Must be dropped.
			name: "premature POM dropped",
			candidates: []domain.VersionInfo{
				{GroupID: groupID, ArtifactID: artifactID, Version: "0.9.0"},
				{GroupID: groupID, ArtifactID: artifactID, Version: "1.0.0"},
				{GroupID: groupID, ArtifactID: artifactID, Version: "2.0.0"}, // no assets yet
			},
			existingInDB: []string{"0.9.0"},
			hasAssets:    map[string]bool{"1.0.0": true, "2.0.0": false},
			wantAccepted: []domain.VersionInfo{
				{GroupID: groupID, ArtifactID: artifactID, Version: "0.9.0"},
				{GroupID: groupID, ArtifactID: artifactID, Version: "1.0.0"},
			},
		},
		{
			name: "already-stored versions bypass probe and pass through",
			candidates: []domain.VersionInfo{
				{GroupID: groupID, ArtifactID: artifactID, Version: "1.0.0"},
				{GroupID: groupID, ArtifactID: artifactID, Version: "2.0.0"},
			},
			existingInDB: []string{"1.0.0"},
			hasAssets:    map[string]bool{"2.0.0": true}, // only 2.0.0 probed
			wantAccepted: []domain.VersionInfo{
				{GroupID: groupID, ArtifactID: artifactID, Version: "1.0.0"},
				{GroupID: groupID, ArtifactID: artifactID, Version: "2.0.0"},
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			mockClient := sonatypemocks.NewMockClient(t)
			mockRepo := repomocks.NewMockRepository(t)

			mockClient.EXPECT().FetchVersionsFromMetadata(mock.Anything, groupID, artifactID).
				Return(tt.candidates, nil)

			mockRepo.EXPECT().GetArtifactByGroupAndId(mock.Anything, db.GetArtifactByGroupAndIdParams{
				GroupID: groupID, ArtifactID: artifactID,
			}).Return(artifactRow, nil)

			existing := tt.existingInDB
			if existing == nil {
				existing = []string{}
			}
			mockRepo.EXPECT().ListArtifactVersionStringsByArtifactID(mock.Anything, db.ListArtifactVersionStringsByArtifactIDParams{
				ArtifactID: 7, Limit: 100, Offset: 0,
			}).Return(existing, nil)

			for v, has := range tt.hasAssets {
				mockClient.EXPECT().VersionHasAssets(mock.Anything, groupID, artifactID, v).
					Return(has, nil)
			}

			a := &activity.VersionSyncActivities{SonatypeClient: mockClient, Repo: mockRepo}
			env := newActivityEnv(t)
			env.RegisterActivity(a.FetchVersions)
			val, err := env.ExecuteActivity(a.FetchVersions, activity.FetchVersionsInput{
				GroupID:    groupID,
				ArtifactID: artifactID,
				Source:     activity.VersionSourceMetadata,
			})
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			var got activity.FetchVersionsOutput
			if err := val.Get(&got); err != nil {
				t.Fatalf("decoding output: %v", err)
			}
			if diff := cmp.Diff(tt.wantAccepted, got.Versions); diff != "" {
				t.Fatalf("versions diff (-want +got):\n%s", diff)
			}
		})
	}
}

// TestFetchVersionsMetadataInitialSeedSkipped verifies that when the database
// has no rows for the artifact yet, the metadata path returns nil without
// probing. Initial seeding is deferred to the hourly full (search) schedule,
// which is ghost-row-safe by construction and can handle thousands of
// versions without blowing the 30s activity timeout.
func TestFetchVersionsMetadataInitialSeedSkipped(t *testing.T) {
	t.Parallel()

	const groupID, artifactID = "org.spongepowered", "spongeapi"
	artifactRow := db.Artifact{ID: 9, GroupID: groupID, ArtifactID: artifactID, Name: "SpongeAPI"}

	mockClient := sonatypemocks.NewMockClient(t)
	mockRepo := repomocks.NewMockRepository(t)

	mockClient.EXPECT().FetchVersionsFromMetadata(mock.Anything, groupID, artifactID).
		Return([]domain.VersionInfo{
			{GroupID: groupID, ArtifactID: artifactID, Version: "1.0.0"},
			{GroupID: groupID, ArtifactID: artifactID, Version: "2.0.0"},
		}, nil)

	mockRepo.EXPECT().GetArtifactByGroupAndId(mock.Anything, db.GetArtifactByGroupAndIdParams{
		GroupID: groupID, ArtifactID: artifactID,
	}).Return(artifactRow, nil)

	mockRepo.EXPECT().ListArtifactVersionStringsByArtifactID(mock.Anything, db.ListArtifactVersionStringsByArtifactIDParams{
		ArtifactID: 9, Limit: 100, Offset: 0,
	}).Return([]string{}, nil)
	// No VersionHasAssets calls — initial seed defers entirely. mockery strict
	// expectations will fail the test if an unexpected call happens.

	a := &activity.VersionSyncActivities{SonatypeClient: mockClient, Repo: mockRepo}
	env := newActivityEnv(t)
	env.RegisterActivity(a.FetchVersions)
	val, err := env.ExecuteActivity(a.FetchVersions, activity.FetchVersionsInput{
		GroupID:    groupID,
		ArtifactID: artifactID,
		Source:     activity.VersionSourceMetadata,
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	var got activity.FetchVersionsOutput
	if err := val.Get(&got); err != nil {
		t.Fatalf("decoding output: %v", err)
	}
	if len(got.Versions) != 0 {
		t.Fatalf("initial seed should return empty, got %d versions", len(got.Versions))
	}
}

// TestFetchVersionsMetadataEmpty verifies that an empty maven-metadata.xml
// (artifact registered but nothing published yet) returns cleanly without
// probing anything.
func TestFetchVersionsMetadataEmpty(t *testing.T) {
	t.Parallel()

	mockClient := sonatypemocks.NewMockClient(t)
	mockClient.EXPECT().FetchVersionsFromMetadata(mock.Anything, "g", "a").
		Return(nil, nil)

	a := &activity.VersionSyncActivities{SonatypeClient: mockClient, Repo: repomocks.NewMockRepository(t)}
	env := newActivityEnv(t)
	env.RegisterActivity(a.FetchVersions)
	val, err := env.ExecuteActivity(a.FetchVersions, activity.FetchVersionsInput{
		GroupID:    "g",
		ArtifactID: "a",
		Source:     activity.VersionSourceMetadata,
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	var got activity.FetchVersionsOutput
	if err := val.Get(&got); err != nil {
		t.Fatalf("decoding output: %v", err)
	}
	if len(got.Versions) != 0 {
		t.Fatalf("expected empty versions, got %d", len(got.Versions))
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

				tx.EXPECT().InsertNewArtifactVersion(mock.Anything, db.InsertNewArtifactVersionParams{
					ArtifactID: 42, Version: "8.0.0",
				}).Return(nil)

				tx.EXPECT().InsertNewArtifactVersion(mock.Anything, db.InsertNewArtifactVersionParams{
					ArtifactID: 42, Version: "7.4.0",
				}).Return(nil)

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

				tx.EXPECT().InsertNewArtifactVersion(mock.Anything, db.InsertNewArtifactVersionParams{
					ArtifactID: 42, Version: "8.0.0",
				}).Return(nil)

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

				tx.EXPECT().InsertNewArtifactVersion(mock.Anything, db.InsertNewArtifactVersionParams{
					ArtifactID: 42, Version: "8.0.0",
				}).Return(errors.New("some db error"))

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
