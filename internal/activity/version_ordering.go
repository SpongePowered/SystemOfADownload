package activity

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"slices"

	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"go.temporal.io/sdk/activity"

	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/repository"
)

// VersionOrderingActivities holds dependencies for version ordering activities.
type VersionOrderingActivities struct {
	Repo       repository.Repository
	HTTPClient *http.Client // nil defaults to an otelhttp-instrumented client
}

// NewVersionOrderingActivities creates a new VersionOrderingActivities with an instrumented HTTP client.
func NewVersionOrderingActivities(repo repository.Repository) *VersionOrderingActivities {
	return &VersionOrderingActivities{
		Repo: repo,
		HTTPClient: &http.Client{
			Transport: otelhttp.NewTransport(http.DefaultTransport),
		},
	}
}

func (a *VersionOrderingActivities) client() *http.Client {
	if a.HTTPClient != nil {
		return a.HTTPClient
	}
	return &http.Client{Transport: otelhttp.NewTransport(http.DefaultTransport)}
}

// FetchMojangManifestInput is the input for the FetchMojangManifest activity.
type FetchMojangManifestInput struct {
	ManifestURL string
}

// FetchMojangManifestOutput contains version ordering from the Mojang manifest.
// The map keys are version strings and values are positions (higher = newer).
type FetchMojangManifestOutput struct {
	VersionOrder map[string]int
}

type mojangManifest struct {
	Versions []mojangVersion `json:"versions"`
}

type mojangVersion struct {
	ID string `json:"id"`
}

const DefaultMojangManifestURL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

// FetchMojangManifest downloads the Mojang version manifest and returns an
// ordering map where higher values mean newer versions.
func (a *VersionOrderingActivities) FetchMojangManifest(ctx context.Context, input FetchMojangManifestInput) (*FetchMojangManifestOutput, error) {
	logger := activity.GetLogger(ctx)

	url := input.ManifestURL
	if url == "" {
		url = DefaultMojangManifestURL
	}

	logger.Info("fetching Mojang version manifest", "url", url)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, http.NoBody)
	if err != nil {
		return nil, fmt.Errorf("creating request: %w", err)
	}

	resp, err := a.client().Do(req)
	if err != nil {
		return nil, fmt.Errorf("fetching manifest: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("unexpected status %d from manifest", resp.StatusCode)
	}

	var manifest mojangManifest
	if err := json.NewDecoder(resp.Body).Decode(&manifest); err != nil {
		return nil, fmt.Errorf("decoding manifest: %w", err)
	}

	// Manifest lists newest first. Assign positions so that newer = higher.
	order := make(map[string]int, len(manifest.Versions))
	total := len(manifest.Versions)
	for i, v := range manifest.Versions {
		order[v.ID] = total - i
	}

	logger.Info("fetched Mojang manifest", "versionCount", total)
	return &FetchMojangManifestOutput{VersionOrder: order}, nil
}

// FetchVersionSchemaInput is the input for the FetchVersionSchema activity.
type FetchVersionSchemaInput struct {
	GroupID    string
	ArtifactID string
}

// FetchVersionSchemaOutput contains the parsed version schema for an artifact.
type FetchVersionSchemaOutput struct {
	Schema *domain.VersionSchema
}

// FetchVersionSchema loads the version schema for an artifact from the database.
// Returns a nil Schema if no schema is configured for the artifact.
func (a *VersionOrderingActivities) FetchVersionSchema(ctx context.Context, input FetchVersionSchemaInput) (*FetchVersionSchemaOutput, error) {
	logger := activity.GetLogger(ctx)
	logger.Info("fetching version schema",
		"groupID", input.GroupID, "artifactID", input.ArtifactID)

	raw, err := a.Repo.GetArtifactVersionSchema(ctx, db.GetArtifactVersionSchemaParams{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
	})
	if err != nil {
		return nil, fmt.Errorf("fetching version schema: %w", err)
	}

	if len(raw) == 0 {
		logger.Info("no version schema configured",
			"groupID", input.GroupID, "artifactID", input.ArtifactID)
		return &FetchVersionSchemaOutput{}, nil
	}

	var schema domain.VersionSchema
	if err := json.Unmarshal(raw, &schema); err != nil {
		return nil, fmt.Errorf("unmarshaling version schema: %w", err)
	}

	if err := schema.Validate(); err != nil {
		return nil, fmt.Errorf("invalid version schema for %s/%s: %w", input.GroupID, input.ArtifactID, err)
	}

	logger.Info("loaded version schema",
		"groupID", input.GroupID, "artifactID", input.ArtifactID,
		"variantCount", len(schema.Variants), "useMojangManifest", schema.UseMojangManifest)
	return &FetchVersionSchemaOutput{Schema: &schema}, nil
}

// ComputeVersionOrderingInput is the input for the ComputeVersionOrdering activity.
type ComputeVersionOrderingInput struct {
	GroupID       string
	ArtifactID    string
	Schema        *domain.VersionSchema // optional, for schema-driven version parsing
	ManifestOrder map[string]int        // optional, from Mojang manifest
}

// VersionSortAssignment maps a version's database ID to its computed sort order.
type VersionSortAssignment struct {
	VersionID   int64
	SortOrder   int32
	Recommended bool
}

// VersionTagSet holds the extracted tags for a single version.
type VersionTagSet struct {
	VersionID int64
	Tags      map[string]string
}

// ComputeVersionOrderingOutput is the output of the ComputeVersionOrdering activity.
type ComputeVersionOrderingOutput struct {
	Assignments []VersionSortAssignment
	// VersionTags contains schema-extracted tags for versions that have them.
	// Only populated when a Schema is provided in the input.
	VersionTags []VersionTagSet
}

// ComputeVersionOrdering fetches all versions for an artifact from the database,
// parses and sorts them, and returns sort_order assignments (higher = newer).
func (a *VersionOrderingActivities) ComputeVersionOrdering(ctx context.Context, input ComputeVersionOrderingInput) (*ComputeVersionOrderingOutput, error) {
	logger := activity.GetLogger(ctx)
	logger.Info("computing version ordering",
		"groupID", input.GroupID, "artifactID", input.ArtifactID,
		"hasSchema", input.Schema != nil, "hasManifest", input.ManifestOrder != nil)

	versions, err := a.Repo.ListArtifactVersions(ctx, db.ListArtifactVersionsParams{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
	})
	if err != nil {
		return nil, fmt.Errorf("listing versions: %w", err)
	}

	if len(versions) == 0 {
		logger.Info("no versions found", "groupID", input.GroupID, "artifactID", input.ArtifactID)
		return &ComputeVersionOrderingOutput{}, nil
	}

	logger.Info("loaded versions from database", "count", len(versions))

	type versionWithID struct {
		dbID   int64
		parsed domain.ParsedVersion
	}

	items := make([]versionWithID, len(versions))
	unmatchedCount := 0
	for i, v := range versions {
		pv := domain.ParseVersionWithSchema(v.Version, input.Schema)

		if input.Schema != nil && len(pv.Segments) == 0 {
			unmatchedCount++
			if unmatchedCount <= 5 {
				logger.Warn("version did not match any schema variant",
					"version", v.Version)
			}
		}

		// Apply manifest ordering if available.
		if input.ManifestOrder != nil {
			mcStr := pv.MinecraftVersionString()
			if pos, ok := input.ManifestOrder[mcStr]; ok {
				pv.ManifestOrder = pos
				// For composite versions, also set it on the minecraft
				// sub-component so recursive comparison uses it.
				if pv.IsComposite && pv.Minecraft != nil {
					pv.Minecraft.ManifestOrder = pos
				}
			}
		}

		items[i] = versionWithID{dbID: v.ID, parsed: pv}
	}

	if unmatchedCount > 0 {
		logger.Warn("versions unmatched by schema", "count", unmatchedCount, "total", len(versions))
	}

	// Sort oldest → newest.
	slices.SortStableFunc(items, func(a, b versionWithID) int {
		return domain.CompareVersions(a.parsed, b.parsed)
	})

	// Assign sort_order: 1 = oldest, N = newest.
	assignments := make([]VersionSortAssignment, len(items))
	for i := range items {
		assignments[i] = VersionSortAssignment{
			VersionID:   items[i].dbID,
			SortOrder:   int32(i + 1),
			Recommended: items[i].parsed.Qualifier == domain.QualifierRelease,
		}
	}

	// Extract tags from schema-parsed versions.
	var versionTags []VersionTagSet
	if input.Schema != nil {
		for i := range items {
			tags := items[i].parsed.ExtractTags()
			if len(tags) > 0 {
				versionTags = append(versionTags, VersionTagSet{
					VersionID: items[i].dbID,
					Tags:      tags,
				})
			}
		}
	}

	logger.Info("computed version ordering",
		"assignments", len(assignments), "tagged", len(versionTags),
		"unmatched", unmatchedCount)

	return &ComputeVersionOrderingOutput{
		Assignments: assignments,
		VersionTags: versionTags,
	}, nil
}

// ApplyVersionOrderingInput is the input for the ApplyVersionOrdering activity.
type ApplyVersionOrderingInput struct {
	Assignments []VersionSortAssignment
}

// ApplyVersionOrdering batch-updates sort_order for all versions in a transaction.
func (a *VersionOrderingActivities) ApplyVersionOrdering(ctx context.Context, input ApplyVersionOrderingInput) error {
	logger := activity.GetLogger(ctx)
	if len(input.Assignments) == 0 {
		return nil
	}

	logger.Info("applying version ordering batch",
		"count", len(input.Assignments),
		"sortRange", fmt.Sprintf("%d-%d", input.Assignments[0].SortOrder, input.Assignments[len(input.Assignments)-1].SortOrder))

	return a.Repo.WithTx(ctx, func(tx repository.Tx) error {
		for _, assign := range input.Assignments {
			err := tx.UpdateArtifactVersionOrder(ctx, db.UpdateArtifactVersionOrderParams{
				ID:          assign.VersionID,
				SortOrder:   assign.SortOrder,
				Recommended: assign.Recommended,
			})
			if err != nil {
				return fmt.Errorf("updating sort_order for version %d: %w", assign.VersionID, err)
			}
		}
		return nil
	})
}

// StoreVersionTagsInput is the input for the StoreVersionTags activity.
type StoreVersionTagsInput struct {
	VersionTags []VersionTagSet
}

// StoreVersionTags replaces schema-extracted tags for all versions in a transaction.
// Existing tags are deleted first to remove stale keys from previous schema versions.
func (a *VersionOrderingActivities) StoreVersionTags(ctx context.Context, input StoreVersionTagsInput) error {
	logger := activity.GetLogger(ctx)
	if len(input.VersionTags) == 0 {
		return nil
	}

	tagCount := 0
	for _, vt := range input.VersionTags {
		tagCount += len(vt.Tags)
	}
	logger.Info("storing version tags batch",
		"versions", len(input.VersionTags), "tags", tagCount)

	return a.Repo.WithTx(ctx, func(tx repository.Tx) error {
		for _, vt := range input.VersionTags {
			// Delete existing tags first to remove stale keys.
			if err := tx.DeleteArtifactVersionTags(ctx, vt.VersionID); err != nil {
				return fmt.Errorf("clearing tags for version %d: %w", vt.VersionID, err)
			}
			for key, value := range vt.Tags {
				_, err := tx.CreateArtifactVersionTag(ctx, db.CreateArtifactVersionTagParams{
					ArtifactVersionID: vt.VersionID,
					TagKey:            key,
					TagValue:          value,
				})
				if err != nil {
					return fmt.Errorf("storing tag %q for version %d: %w", key, vt.VersionID, err)
				}
			}
		}
		return nil
	})
}
