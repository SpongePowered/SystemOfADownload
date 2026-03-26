package activity_test

import (
	"archive/zip"
	"bytes"
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
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

func TestFetchVersionAssets(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name      string
		input     activity.FetchVersionAssetsInput
		mockSetup func(m *sonatypemocks.MockClient)
		want      *activity.FetchVersionAssetsOutput
		wantErr   bool
	}{
		{
			name: "successful fetch",
			input: activity.FetchVersionAssetsInput{
				GroupID: "org.spongepowered", ArtifactID: "spongeapi", Version: "8.0.0",
			},
			mockSetup: func(m *sonatypemocks.MockClient) {
				m.EXPECT().SearchAssets(mock.Anything, "org.spongepowered", "spongeapi", "8.0.0").
					Return([]domain.AssetInfo{
						{DownloadURL: "https://repo.example/spongeapi-8.0.0.jar", Extension: "jar"},
						{DownloadURL: "https://repo.example/spongeapi-8.0.0.pom", Extension: "pom"},
					}, nil)
			},
			want: &activity.FetchVersionAssetsOutput{
				Assets: []domain.AssetInfo{
					{DownloadURL: "https://repo.example/spongeapi-8.0.0.jar", Extension: "jar"},
					{DownloadURL: "https://repo.example/spongeapi-8.0.0.pom", Extension: "pom"},
				},
			},
		},
		{
			name: "API error propagates",
			input: activity.FetchVersionAssetsInput{
				GroupID: "org.spongepowered", ArtifactID: "spongeapi", Version: "8.0.0",
			},
			mockSetup: func(m *sonatypemocks.MockClient) {
				m.EXPECT().SearchAssets(mock.Anything, mock.Anything, mock.Anything, mock.Anything).
					Return(nil, errors.New("search failed"))
			},
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			mockClient := sonatypemocks.NewMockClient(t)
			tt.mockSetup(mockClient)

			a := &activity.VersionIndexActivities{SonatypeClient: mockClient}
			got, err := a.FetchVersionAssets(t.Context(), tt.input)

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
				t.Fatalf("diff (-want +got):\n%s", diff)
			}
		})
	}
}

func TestStoreVersionAssets(t *testing.T) {
	t.Parallel()

	const (
		groupID    = "org.spongepowered"
		artifactID = "spongeapi"
		version    = "8.0.0"
	)
	avRow := db.ArtifactVersion{ID: 10, ArtifactID: 1, Version: version}

	tests := []struct {
		name      string
		input     activity.StoreVersionAssetsInput
		mockSetup func(repo *repomocks.MockRepository, tx *repomocks.MockTx)
		want      *activity.StoreVersionAssetsOutput
		wantErr   bool
	}{
		{
			name: "stores assets in transaction",
			input: activity.StoreVersionAssetsInput{
				GroupID: groupID, ArtifactID: artifactID, Version: version,
				Assets: []domain.AssetInfo{
					{DownloadURL: "https://repo.example/spongeapi-8.0.0.jar", Classifier: "sources", Sha256: "abc123"},
				},
			},
			mockSetup: func(repo *repomocks.MockRepository, tx *repomocks.MockTx) {
				repo.EXPECT().GetArtifactVersion(mock.Anything, db.GetArtifactVersionParams{
					GroupID: groupID, ArtifactID: artifactID, Version: version,
				}).Return(avRow, nil)

				classifier := "sources"
				sha := "abc123"
				tx.EXPECT().CreateArtifactVersionAsset(mock.Anything, db.CreateArtifactVersionAssetParams{
					ArtifactVersionID: 10,
					Classifier:        &classifier,
					Sha256:            &sha,
					DownloadUrl:       "https://repo.example/spongeapi-8.0.0.jar",
				}).Return(db.ArtifactVersionedAsset{}, nil)

				repo.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(repository.Tx) error) error {
						return fn(tx)
					},
				)
			},
			want: &activity.StoreVersionAssetsOutput{StoredCount: 1},
		},
		{
			name: "version lookup error propagates",
			input: activity.StoreVersionAssetsInput{
				GroupID: groupID, ArtifactID: artifactID, Version: version,
				Assets: []domain.AssetInfo{{DownloadURL: "https://example.com/a.jar"}},
			},
			mockSetup: func(repo *repomocks.MockRepository, tx *repomocks.MockTx) {
				repo.EXPECT().GetArtifactVersion(mock.Anything, mock.Anything).
					Return(db.ArtifactVersion{}, errors.New("not found"))
			},
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			mockRepo := repomocks.NewMockRepository(t)
			mockTx := repomocks.NewMockTx(t)
			tt.mockSetup(mockRepo, mockTx)

			a := &activity.VersionIndexActivities{Repo: mockRepo}
			got, err := a.StoreVersionAssets(t.Context(), tt.input)

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
				t.Fatalf("diff (-want +got):\n%s", diff)
			}
		})
	}
}

func TestBuildAndStoreTags(t *testing.T) {
	t.Parallel()

	const (
		groupID    = "org.spongepowered"
		artifactID = "spongeapi"
		version    = "8.0.0"
	)
	avRow := db.ArtifactVersion{ID: 10, ArtifactID: 1, Version: version}

	tests := []struct {
		name      string
		input     activity.BuildAndStoreTagsInput
		mockSetup func(repo *repomocks.MockRepository, tx *repomocks.MockTx)
		want      *activity.BuildAndStoreTagsOutput
		wantErr   bool
	}{
		{
			name: "matches tag rule and stores",
			input: activity.BuildAndStoreTagsInput{
				GroupID: groupID, ArtifactID: artifactID, Version: version,
				Assets: []domain.AssetInfo{
					{Classifier: "universal", Extension: "jar"},
				},
				TagRules: []domain.ArtifactTag{
					{Name: "platform", Regex: "universal", Test: "universal", MarkAsRecommended: true},
				},
			},
			mockSetup: func(repo *repomocks.MockRepository, tx *repomocks.MockTx) {
				repo.EXPECT().GetArtifactVersion(mock.Anything, db.GetArtifactVersionParams{
					GroupID: groupID, ArtifactID: artifactID, Version: version,
				}).Return(avRow, nil)

				tx.EXPECT().CreateArtifactVersionTag(mock.Anything, db.CreateArtifactVersionTagParams{
					ArtifactVersionID: 10, TagKey: "platform", TagValue: "universal",
				}).Return(db.ArtifactVersionedTag{}, nil)

				repo.EXPECT().WithTx(mock.Anything, mock.Anything).RunAndReturn(
					func(ctx context.Context, fn func(repository.Tx) error) error {
						return fn(tx)
					},
				)
			},
			want: &activity.BuildAndStoreTagsOutput{TagsCreated: 1, Recommended: true},
		},
		{
			name: "no matching rules returns empty",
			input: activity.BuildAndStoreTagsInput{
				GroupID: groupID, ArtifactID: artifactID, Version: version,
				Assets: []domain.AssetInfo{
					{Classifier: "sources", Extension: "jar"},
				},
				TagRules: []domain.ArtifactTag{
					{Name: "platform", Regex: "^universal$", Test: "universal"},
				},
			},
			mockSetup: func(repo *repomocks.MockRepository, tx *repomocks.MockTx) {
				repo.EXPECT().GetArtifactVersion(mock.Anything, mock.Anything).Return(avRow, nil)
			},
			want: &activity.BuildAndStoreTagsOutput{},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			mockRepo := repomocks.NewMockRepository(t)
			mockTx := repomocks.NewMockTx(t)
			tt.mockSetup(mockRepo, mockTx)

			a := &activity.VersionIndexActivities{Repo: mockRepo}
			got, err := a.BuildAndStoreTags(t.Context(), tt.input)

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
				t.Fatalf("diff (-want +got):\n%s", diff)
			}
		})
	}
}

func TestPickBestJarCandidate(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name   string
		assets []domain.AssetInfo
		want   *activity.JarCommitCandidate
	}{
		{
			name: "prefers main jar (empty classifier)",
			assets: []domain.AssetInfo{
				{DownloadURL: "https://repo.example/a.jar", Extension: "jar", Classifier: ""},
				{DownloadURL: "https://repo.example/a.pom", Extension: "pom"},
				{DownloadURL: "https://repo.example/a-sources.jar", Extension: "jar", Classifier: "sources"},
			},
			want: &activity.JarCommitCandidate{DownloadURL: "https://repo.example/a.jar", Classifier: ""},
		},
		{
			name: "falls back to first jar when no empty classifier",
			assets: []domain.AssetInfo{
				{DownloadURL: "https://repo.example/a.pom", Extension: "pom"},
				{DownloadURL: "https://repo.example/a-sources.jar", Extension: "jar", Classifier: "sources"},
			},
			want: &activity.JarCommitCandidate{DownloadURL: "https://repo.example/a-sources.jar", Classifier: "sources"},
		},
		{
			name: "no jars returns nil",
			assets: []domain.AssetInfo{
				{DownloadURL: "https://repo.example/a.pom", Extension: "pom"},
			},
			want: nil,
		},
		{
			name:   "empty assets returns nil",
			assets: nil,
			want:   nil,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			got := activity.PickBestJarCandidate(tt.assets)
			if diff := cmp.Diff(tt.want, got); diff != "" {
				t.Fatalf("diff (-want +got):\n%s", diff)
			}
		})
	}
}

func TestExtractCommitFromJar(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name    string
		jarData func() []byte
		want    *activity.ExtractCommitFromJarOutput
		wantErr bool
	}{
		{
			name: "extracts commit from git.properties",
			jarData: func() []byte {
				return createTestJar(t, map[string]string{
					"META-INF/git.properties": "git.commit.id=abc123def\ngit.remote.origin.url=https://github.com/SpongePowered/Sponge\n",
				})
			},
			want: &activity.ExtractCommitFromJarOutput{
				CommitInfo: &domain.CommitInfo{
					Sha:        "abc123def",
					Repository: "https://github.com/SpongePowered/Sponge",
				},
			},
		},
		{
			name: "extracts commit from MANIFEST.MF",
			jarData: func() []byte {
				return createTestJar(t, map[string]string{
					"META-INF/MANIFEST.MF": "Manifest-Version: 1.0\nGit-Commit: deadbeef\nGit-Repository: https://github.com/SpongePowered/SpongeAPI\n",
				})
			},
			want: &activity.ExtractCommitFromJarOutput{
				CommitInfo: &domain.CommitInfo{
					Sha:        "deadbeef",
					Repository: "https://github.com/SpongePowered/SpongeAPI",
				},
			},
		},
		{
			name: "no commit info returns nil",
			jarData: func() []byte {
				return createTestJar(t, map[string]string{
					"META-INF/MANIFEST.MF": "Manifest-Version: 1.0\nCreated-By: Maven\n",
				})
			},
			want: &activity.ExtractCommitFromJarOutput{},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			jarBytes := tt.jarData()
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.Header().Set("Content-Type", "application/java-archive")
				w.Write(jarBytes)
			}))
			t.Cleanup(server.Close)

			a := &activity.VersionIndexActivities{HTTPClient: server.Client()}
			got, err := a.ExtractCommitFromJar(t.Context(), activity.ExtractCommitFromJarInput{
				DownloadURL: server.URL + "/test.jar",
			})

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
				t.Fatalf("diff (-want +got):\n%s", diff)
			}
		})
	}
}

// createTestJar builds an in-memory zip (jar) with the given file contents.
func createTestJar(t *testing.T, files map[string]string) []byte {
	t.Helper()
	var buf bytes.Buffer
	w := zip.NewWriter(&buf)
	for name, content := range files {
		f, err := w.Create(name)
		if err != nil {
			t.Fatalf("creating zip entry %s: %v", name, err)
		}
		if _, err := f.Write([]byte(content)); err != nil {
			t.Fatalf("writing zip entry %s: %v", name, err)
		}
	}
	if err := w.Close(); err != nil {
		t.Fatalf("closing zip: %v", err)
	}
	return buf.Bytes()
}
