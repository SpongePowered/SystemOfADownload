package activity

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"slices"

	"github.com/spongepowered/systemofadownload/internal/db"
	"github.com/spongepowered/systemofadownload/internal/domain"
	"github.com/spongepowered/systemofadownload/internal/repository"
)

// VersionOrderingActivities holds dependencies for version ordering activities.
type VersionOrderingActivities struct {
	Repo       repository.Repository
	HTTPClient *http.Client
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
	httpClient := a.HTTPClient
	if httpClient == nil {
		httpClient = http.DefaultClient
	}

	url := input.ManifestURL
	if url == "" {
		url = DefaultMojangManifestURL
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, fmt.Errorf("creating request: %w", err)
	}

	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("fetching manifest: %w", err)
	}
	defer resp.Body.Close()

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

	return &FetchMojangManifestOutput{VersionOrder: order}, nil
}

// ComputeVersionOrderingInput is the input for the ComputeVersionOrdering activity.
type ComputeVersionOrderingInput struct {
	GroupID      string
	ArtifactID   string
	ManifestOrder map[string]int // optional, from Mojang manifest
}

// VersionSortAssignment maps a version's database ID to its computed sort order.
type VersionSortAssignment struct {
	VersionID int64
	SortOrder int32
}

// ComputeVersionOrderingOutput is the output of the ComputeVersionOrdering activity.
type ComputeVersionOrderingOutput struct {
	Assignments []VersionSortAssignment
}

// ComputeVersionOrdering fetches all versions for an artifact from the database,
// parses and sorts them, and returns sort_order assignments (higher = newer).
func (a *VersionOrderingActivities) ComputeVersionOrdering(ctx context.Context, input ComputeVersionOrderingInput) (*ComputeVersionOrderingOutput, error) {
	versions, err := a.Repo.ListArtifactVersions(ctx, db.ListArtifactVersionsParams{
		GroupID:    input.GroupID,
		ArtifactID: input.ArtifactID,
	})
	if err != nil {
		return nil, fmt.Errorf("listing versions: %w", err)
	}

	if len(versions) == 0 {
		return &ComputeVersionOrderingOutput{}, nil
	}

	type versionWithID struct {
		dbID   int64
		parsed domain.ParsedVersion
	}

	items := make([]versionWithID, len(versions))
	for i, v := range versions {
		pv := domain.ParseVersion(v.Version)

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

	// Sort oldest → newest.
	slices.SortStableFunc(items, func(a, b versionWithID) int {
		return domain.CompareVersions(a.parsed, b.parsed)
	})

	// Assign sort_order: 1 = oldest, N = newest.
	assignments := make([]VersionSortAssignment, len(items))
	for i, item := range items {
		assignments[i] = VersionSortAssignment{
			VersionID: item.dbID,
			SortOrder: int32(i + 1),
		}
	}

	return &ComputeVersionOrderingOutput{Assignments: assignments}, nil
}

// ApplyVersionOrderingInput is the input for the ApplyVersionOrdering activity.
type ApplyVersionOrderingInput struct {
	Assignments []VersionSortAssignment
}

// ApplyVersionOrdering batch-updates sort_order for all versions in a transaction.
func (a *VersionOrderingActivities) ApplyVersionOrdering(ctx context.Context, input ApplyVersionOrderingInput) error {
	if len(input.Assignments) == 0 {
		return nil
	}

	return a.Repo.WithTx(ctx, func(tx repository.Tx) error {
		for _, assign := range input.Assignments {
			err := tx.UpdateArtifactVersionOrder(ctx, db.UpdateArtifactVersionOrderParams{
				ID:        assign.VersionID,
				SortOrder: assign.SortOrder,
			})
			if err != nil {
				return fmt.Errorf("updating sort_order for version %d: %w", assign.VersionID, err)
			}
		}
		return nil
	})
}
