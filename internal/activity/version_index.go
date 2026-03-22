package activity

import (
	"archive/zip"
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"strings"

	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/repository"
	"github.com/spongepowered/systemofadownload/internal/sonatype"
)

// VersionIndexActivities holds the dependencies for version indexing activities.
type VersionIndexActivities struct {
	SonatypeClient sonatype.Client
	Repo           repository.Repository
	HTTPClient     *http.Client
}

// FetchVersionAssetsInput is the input for the FetchVersionAssets activity.
type FetchVersionAssetsInput struct {
	GroupID    string
	ArtifactID string
	Version    string
}

// FetchVersionAssetsOutput is the output of the FetchVersionAssets activity.
type FetchVersionAssetsOutput struct {
	Assets []domain.AssetInfo
}

// FetchVersionAssets calls the Sonatype REST API v3 to retrieve assets for a specific version.
func (a *VersionIndexActivities) FetchVersionAssets(ctx context.Context, input FetchVersionAssetsInput) (*FetchVersionAssetsOutput, error) {
	assets, err := a.SonatypeClient.SearchAssets(ctx, input.GroupID, input.ArtifactID, input.Version)
	if err != nil {
		return nil, fmt.Errorf("searching assets for %s:%s:%s: %w", input.GroupID, input.ArtifactID, input.Version, err)
	}
	return &FetchVersionAssetsOutput{Assets: assets}, nil
}

// StoreVersionAssetsInput is the input for the StoreVersionAssets activity.
type StoreVersionAssetsInput struct {
	GroupID    string
	ArtifactID string
	Version    string
	Assets     []domain.AssetInfo
}

// StoreVersionAssetsOutput is the output of the StoreVersionAssets activity.
type StoreVersionAssetsOutput struct {
	StoredCount int
}

// StoreVersionAssets persists version assets to the database.
func (a *VersionIndexActivities) StoreVersionAssets(ctx context.Context, input StoreVersionAssetsInput) (*StoreVersionAssetsOutput, error) {
	av, err := a.Repo.GetArtifactVersion(ctx, db.GetArtifactVersionParams{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
		Version:    input.Version,
	})
	if err != nil {
		return nil, fmt.Errorf("looking up version %s:%s:%s: %w", input.GroupID, input.ArtifactID, input.Version, err)
	}

	stored := 0
	err = a.Repo.WithTx(ctx, func(tx repository.Tx) error {
		for _, asset := range input.Assets {
			classifier := &asset.Classifier
			if asset.Classifier == "" {
				classifier = nil
			}
			sha := &asset.Sha256
			if asset.Sha256 == "" {
				sha = nil
			}
			_, err := tx.CreateArtifactVersionAsset(ctx, db.CreateArtifactVersionAssetParams{
				ArtifactVersionID: av.ID,
				Classifier:        classifier,
				Sha256:            sha,
				DownloadUrl:       asset.DownloadURL,
			})
			if err != nil {
				return fmt.Errorf("creating asset %s: %w", asset.Path, err)
			}
			stored++
		}
		return nil
	})
	if err != nil {
		return nil, fmt.Errorf("storing assets: %w", err)
	}

	return &StoreVersionAssetsOutput{StoredCount: stored}, nil
}

// BuildAndStoreTagsInput is the input for the BuildAndStoreTags activity.
type BuildAndStoreTagsInput struct {
	GroupID    string
	ArtifactID string
	Version    string
	Assets     []domain.AssetInfo
	TagRules   []domain.ArtifactTag
}

// BuildAndStoreTagsOutput is the output of the BuildAndStoreTags activity.
type BuildAndStoreTagsOutput struct {
	TagsCreated   int
	Recommended   bool
}

// BuildAndStoreTags applies tag rules against assets and stores matching tags.
func (a *VersionIndexActivities) BuildAndStoreTags(ctx context.Context, input BuildAndStoreTagsInput) (*BuildAndStoreTagsOutput, error) {
	av, err := a.Repo.GetArtifactVersion(ctx, db.GetArtifactVersionParams{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
		Version:    input.Version,
	})
	if err != nil {
		return nil, fmt.Errorf("looking up version: %w", err)
	}

	type matchedTag struct {
		key               string
		value             string
		markAsRecommended bool
	}

	var matched []matchedTag
	for _, rule := range input.TagRules {
		re, err := regexp.Compile(rule.Regex)
		if err != nil {
			return nil, fmt.Errorf("compiling regex for tag %q: %w", rule.Name, err)
		}
		for _, asset := range input.Assets {
			// Test against classifier and path
			if re.MatchString(asset.Classifier) || re.MatchString(asset.Path) {
				matched = append(matched, matchedTag{
					key:               rule.Name,
					value:             rule.Test,
					markAsRecommended: rule.MarkAsRecommended,
				})
				break // one match per rule is sufficient
			}
		}
	}

	if len(matched) == 0 {
		return &BuildAndStoreTagsOutput{}, nil
	}

	recommended := false
	err = a.Repo.WithTx(ctx, func(tx repository.Tx) error {
		for _, tag := range matched {
			_, err := tx.CreateArtifactVersionTag(ctx, db.CreateArtifactVersionTagParams{
				ArtifactVersionID: av.ID,
				TagKey:            tag.key,
				TagValue:          tag.value,
			})
			if err != nil {
				return fmt.Errorf("creating tag %s=%s: %w", tag.key, tag.value, err)
			}
			if tag.markAsRecommended {
				recommended = true
			}
		}
		return nil
	})
	if err != nil {
		return nil, fmt.Errorf("storing tags: %w", err)
	}

	return &BuildAndStoreTagsOutput{
		TagsCreated: len(matched),
		Recommended: recommended,
	}, nil
}

// InspectJarsForCommitsInput is the input for the InspectJarsForCommits activity.
type InspectJarsForCommitsInput struct {
	Assets []domain.AssetInfo
}

// JarCommitCandidate represents a jar file that may contain a commit SHA.
type JarCommitCandidate struct {
	DownloadURL string
	Classifier  string
}

// InspectJarsForCommitsOutput is the output of the InspectJarsForCommits activity.
type InspectJarsForCommitsOutput struct {
	Candidates []JarCommitCandidate
}

// InspectJarsForCommits identifies jar assets that are candidates for commit extraction.
// It filters assets to jar files and returns them as candidates for the ExtractCommit batch.
func (a *VersionIndexActivities) InspectJarsForCommits(_ context.Context, input InspectJarsForCommitsInput) (*InspectJarsForCommitsOutput, error) {
	var candidates []JarCommitCandidate
	for _, asset := range input.Assets {
		if asset.Extension == "jar" || strings.HasSuffix(asset.Path, ".jar") {
			candidates = append(candidates, JarCommitCandidate{
				DownloadURL: asset.DownloadURL,
				Classifier:  asset.Classifier,
			})
		}
	}
	return &InspectJarsForCommitsOutput{Candidates: candidates}, nil
}

// ExtractCommitFromJarInput is the input for the ExtractCommitFromJar activity.
type ExtractCommitFromJarInput struct {
	DownloadURL string
}

// ExtractCommitFromJarOutput is the output of the ExtractCommitFromJar activity.
type ExtractCommitFromJarOutput struct {
	CommitInfo *domain.CommitInfo
}

// ExtractCommitFromJar downloads a jar and inspects META-INF for commit SHA information.
func (a *VersionIndexActivities) ExtractCommitFromJar(ctx context.Context, input ExtractCommitFromJarInput) (*ExtractCommitFromJarOutput, error) {
	httpClient := a.HTTPClient
	if httpClient == nil {
		httpClient = http.DefaultClient
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, input.DownloadURL, nil)
	if err != nil {
		return nil, fmt.Errorf("creating request: %w", err)
	}

	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("downloading jar: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("unexpected status %d downloading %s", resp.StatusCode, input.DownloadURL)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("reading jar body: %w", err)
	}

	commitInfo, err := extractCommitFromZip(body)
	if err != nil {
		return nil, fmt.Errorf("extracting commit info: %w", err)
	}

	return &ExtractCommitFromJarOutput{CommitInfo: commitInfo}, nil
}

// extractCommitFromZip reads a jar (zip) file and looks for commit information
// in META-INF/git.properties or META-INF/MANIFEST.MF.
func extractCommitFromZip(data []byte) (*domain.CommitInfo, error) {
	reader, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		return nil, fmt.Errorf("opening zip: %w", err)
	}

	for _, f := range reader.File {
		name := strings.ToLower(f.Name)
		switch {
		case strings.HasSuffix(name, "git.properties"):
			return parseGitProperties(f)
		case name == "meta-inf/manifest.mf":
			info, err := parseManifest(f)
			if err != nil {
				return nil, err
			}
			if info != nil {
				return info, nil
			}
		}
	}
	return nil, nil
}

func parseGitProperties(f *zip.File) (*domain.CommitInfo, error) {
	rc, err := f.Open()
	if err != nil {
		return nil, err
	}
	defer rc.Close()

	info := &domain.CommitInfo{}
	scanner := bufio.NewScanner(rc)
	for scanner.Scan() {
		line := scanner.Text()
		if k, v, ok := strings.Cut(line, "="); ok {
			key := strings.TrimSpace(k)
			val := strings.TrimSpace(v)
			switch key {
			case "git.commit.id", "git.commit.id.full":
				info.Sha = val
			case "git.remote.origin.url":
				info.Repository = val
			case "git.branch":
				info.Branch = val
			}
		}
	}
	if info.Sha == "" {
		return nil, nil
	}
	return info, scanner.Err()
}

func parseManifest(f *zip.File) (*domain.CommitInfo, error) {
	rc, err := f.Open()
	if err != nil {
		return nil, err
	}
	defer rc.Close()

	info := &domain.CommitInfo{}
	scanner := bufio.NewScanner(rc)
	for scanner.Scan() {
		line := scanner.Text()
		if k, v, ok := strings.Cut(line, ": "); ok {
			key := strings.TrimSpace(k)
			val := strings.TrimSpace(v)
			switch key {
			case "Implementation-Build", "Git-Commit":
				info.Sha = val
			case "Git-Repository":
				info.Repository = val
			case "Git-Branch":
				info.Branch = val
			}
		}
	}
	if info.Sha == "" {
		return nil, nil
	}
	return info, scanner.Err()
}

// StoreCommitInfoInput is the input for the StoreCommitInfo activity.
type StoreCommitInfoInput struct {
	GroupID    string
	ArtifactID string
	Version    string
	CommitInfo domain.CommitInfo
}

// StoreCommitInfo stores extracted commit metadata on an artifact version.
func (a *VersionIndexActivities) StoreCommitInfo(ctx context.Context, input StoreCommitInfoInput) error {
	av, err := a.Repo.GetArtifactVersion(ctx, db.GetArtifactVersionParams{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
		Version:    input.Version,
	})
	if err != nil {
		return fmt.Errorf("looking up version: %w", err)
	}

	commitJSON, err := marshalCommitBody(input.CommitInfo)
	if err != nil {
		return err
	}

	return a.Repo.WithTx(ctx, func(tx repository.Tx) error {
		return tx.UpdateArtifactVersionCommitBody(ctx, db.UpdateArtifactVersionCommitBodyParams{
			ID:         av.ID,
			CommitBody: commitJSON,
		})
	})
}

func marshalCommitBody(info domain.CommitInfo) ([]byte, error) {
	return json.Marshal(info)
}
