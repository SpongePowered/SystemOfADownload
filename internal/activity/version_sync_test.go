package activity_test

import (
	"errors"
	"testing"

	"github.com/google/go-cmp/cmp"

	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/domain"
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
