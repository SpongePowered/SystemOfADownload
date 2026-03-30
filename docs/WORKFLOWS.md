# Temporal Workflows

SystemOfADownload uses [Temporal](https://temporal.io/) to orchestrate artifact version syncing, asset indexing, commit extraction, and version ordering. All workflows run on a single task queue (`version-sync`) with a single worker process.

## Architecture Overview

```
HTTP API (RegisterArtifact → triggers VersionSyncWorkflow)
         (PutArtifactSchema → triggers VersionOrderingWorkflow)
 |
 v
VersionSyncWorkflow ─────────────────────────────────────────────────┐
 ├── FetchVersions           (Sonatype maven-metadata.xml)           |
 ├── StoreNewVersions        (DB: compare + insert new versions)     |
 ├── VersionBatchIndexWorkflow (child, if new versions or ForceReindex)|
 │    └── VersionIndexWorkflow (per version, sliding window of 5)    |
 │         ├── FetchVersionAssets   (Sonatype REST API)              |
 │         ├── StoreVersionAssets   (DB)                             |
 │         ├── InspectJarsForCommits (pure logic)                    |
 │         └── ExtractCommitBatchWorkflow (child, if jars found)     |
 │              └── ExtractCommitWorkflow (per jar, window of 3)     |
 │                   ├── ExtractCommitFromJar (HTTP + zip parse)     |
 │                   └── StoreCommitInfo     (DB)                    |
 ├── VersionOrderingWorkflow (child, always runs for ALL versions)   |
 │    ├── FetchVersionSchema        (DB)                             |
 │    ├── FetchMojangManifest       (HTTP, optional)                 |
 │    ├── ComputeVersionOrdering    (DB read + sort + tag extract)   |
 │    ├── ApplyVersionOrdering x N  (DB write, batches of 500)       |
 │    └── StoreVersionTags x N     (DB write, batches of 500)        |
 └── CommitEnrichmentWorkflow (child, enriches commits + changelogs) |
      ├── FetchVersionsForEnrichment (DB: versions needing enrichment)|
      ├── EnrichmentBatchWorkflow  (sliding window of 3, parallel)   |
      │    └── EnrichVersionWorkflow (per version)                    |
      │         ├── EnsureRepoCloned       (local: git clone/fetch)  |
      │         ├── GetCommitDetails       (local: git show)         |
      │         ├── ResolveSubmodules      (local: .gitmodules)      |
      │         ├── [per submodule, parallel]                         |
      │         │    ├── EnsureRepoCloned  (local: submodule repo)   |
      │         │    └── GetCommitDetails  (local: submodule SHA)    |
      │         └── StoreEnrichedCommit    (DB)                      |
      └── ChangelogBatchWorkflow   (sequential, sort_order ASC)      |
           └── ChangelogVersionWorkflow (per version)                 |
                ├── GetPreviousVersionCommit (DB)                     |
                ├── [wait for N-1 enrichment if needed]               |
                ├── ComputeChangelog       (local: git log)           |
                ├── [per submodule with changed pointer]              |
                │    └── ComputeChangelog  (local: submodule log)     |
                └── StoreChangelog         (DB)                       |
──────────────────────────────────────────────────────────────────────┘
```

## Workflow Details

### VersionSyncWorkflow

The top-level orchestrator, triggered when an artifact is registered via the HTTP API. It coordinates the entire pipeline from fetching versions through to ordering.

**Trigger:** `POST /groups/{groupID}/artifacts` (fire-and-forget)

**Input:** `VersionSyncInput{GroupID, ArtifactID, ForceReindex}`

**Steps:**
1. Fetch all version strings from Sonatype Nexus (`maven-metadata.xml`)
2. Compare against DB and store only new versions
3. Launch batch indexing for new versions (assets + commits) — or **all** versions when `ForceReindex` is true
4. Compute version ordering and extract schema-driven tags for **all** versions

The ordering step runs for all versions (not just new ones) because `sort_order` is a relative ranking that must be recomputed when new versions are inserted. The `ForceReindex` flag is useful for backfilling data after schema changes (e.g. new asset columns).

### VersionBatchIndexWorkflow

Processes new versions using a **sliding window** pattern for bounded concurrency.

**Pattern:** Maintains up to 5 concurrent `VersionIndexWorkflow` children. Each child signals the parent on completion, freeing a window slot. Uses `ContinueAsNew` for pagination across large version sets (10 versions per workflow run).

This pattern prevents unbounded fan-out that could overwhelm the Sonatype API or database.

### VersionIndexWorkflow

Processes a single version: fetches assets from Sonatype, stores them, identifies jar files, and extracts git commit metadata from jar manifests.

**Steps:**
1. Fetch assets from Sonatype REST API (`/v3/search/assets`)
2. Store assets in `artifact_versioned_assets`
3. Identify `.jar` files as commit extraction candidates
4. Launch `ExtractCommitBatchWorkflow` if candidates found
5. Signal parent `VersionBatchIndexWorkflow` on completion

### ExtractCommitBatchWorkflow / ExtractCommitWorkflow

Downloads jar files and parses `META-INF/git.properties` or `META-INF/MANIFEST.MF` to extract git commit SHAs and repository URLs. Uses the same sliding window pattern as batch indexing (window size 3, page size 5).

### VersionOrderingWorkflow

Computes version sort ordering using schema-driven parsing and optionally the Mojang version manifest for correct Minecraft version placement.

**Steps:**
1. **Fetch schema** from DB (`artifacts.version_schema` JSONB column)
2. **Fetch Mojang manifest** if `schema.UseMojangManifest` is true
3. **Compute ordering**: parse all versions with schema, apply manifest positions, sort, assign `sort_order` ranks, extract tags
4. **Apply ordering** in batches of 500 (update `sort_order` in DB)
5. **Store tags** in batches of 500 (delete stale + upsert new tags)

## Version Schema

Each artifact can have a `version_schema` stored as JSONB that defines how its version strings are parsed. The schema uses regex named capture groups to extract components like Minecraft version, API version, and platform-specific versions (Forge, NeoForge).

```json
{
  "use_mojang_manifest": true,
  "variants": [
    {
      "name": "current",
      "pattern": "^(?P<minecraft>...)-(?P<api>\\d+\\.\\d+\\.\\d+)(?:-(?P<qualifier>RC)(?P<build>\\d+))?$",
      "segments": [
        {"name": "minecraft", "parse_as": "minecraft", "tag_key": "minecraft"},
        {"name": "api", "parse_as": "dotted", "tag_key": "api"}
      ]
    },
    {
      "name": "beta-era",
      "pattern": "^(?P<minecraft>...)-(?P<api>\\d+\\.\\d+\\.\\d+)-(?P<qualifier>BETA)-(?P<build>\\d+)$",
      "segments": [...]
    }
  ]
}
```

**Variants** are tried in order; the first regex match wins. This allows a single schema to handle multiple historical version formats (e.g., current RC format and legacy BETA format).

**Segment rules** control how each captured group is parsed for comparison:
- `minecraft` — recursively parsed as a Minecraft version (handles `1.21.10`, `25w41a`, `1.21.11-pre1`, `26.1-snapshot-2`)
- `dotted` — dot-separated integers (`17.0.1` → `[17, 0, 1]`)
- `integer` — single integer (`2838`)
- `ignore` — captured but not used in comparison

**Tag extraction:** Segments with a `tag_key` produce version tags stored in `artifact_versioned_tags`. For example, parsing `1.21.10-17.0.1-RC2547` produces tags `minecraft=1.21.10` and `api=17.0.1`.

**Variant ordering:** When comparing versions from different variants (e.g., a beta-era version vs a current-format version with the same Minecraft version), the variant's position in the list acts as a tiebreaker. Variants should be listed newest-first — earlier variants sort as newer.

**Schema management:** The version schema is managed via `PUT /groups/{groupID}/artifacts/{artifactID}/schema`. Updating the schema triggers a `VersionOrderingWorkflow` with `TERMINATE_EXISTING` conflict policy — any in-flight ordering with stale schema is cancelled and restarted.

## Workflow Conflict Policies

| Trigger | Workflow | Conflict Policy | Behavior |
|---------|----------|----------------|----------|
| `RegisterArtifact` | VersionSyncWorkflow | `USE_EXISTING` | If already running, return handle to existing run |
| `PutArtifactSchema` | VersionOrderingWorkflow | `TERMINATE_EXISTING` | Cancel stale run, restart with new schema |

Both use `ALLOW_DUPLICATE` reuse policy so workflows can be re-run after completion.

### CommitEnrichmentWorkflow

Enriches version commit records with full git details (message, author, date, submodule state) and computes changelogs between consecutive versions. Runs after `VersionOrderingWorkflow` so sort order is known.

**Two-phase approach:**

1. **Enrichment phase** (parallel): For each version with a commit SHA but no enrichment, clones the git repo (bare, cached on the worker), resolves full commit details via `git show`, and resolves submodule SHAs from `.gitmodules`. Different versions can be enriched in parallel.

2. **Changelog phase** (sequential): For each version in ascending sort order, computes `git log prevSHA..curSHA` for the main repo and each submodule where the pointer changed. Sequential processing guarantees N-1 is enriched before N computes its changelog.

**Worker-local git cache:** Git repos are stored as bare clones under `$GIT_CACHE_DIR` (default `/var/cache/soad/git`). A per-repo mutex prevents concurrent git operations on the same directory. `MaxConcurrentLocalActivityExecutionSize: 4` caps total simultaneous git operations.

**Repo transitions:** When consecutive versions use different repositories (e.g., SpongeForge repo for <=1.12, Sponge repo for >=1.16), the main-repo changelog is skipped since there's no common git history. Submodule changelogs may still be computed if both versions share a common submodule.

**Dependency resolution:** If version N-1's enrichment hasn't completed when N's changelog is due, the workflow polls every 30s for up to 10 minutes. On timeout, the version is marked `changelogStatus: "pending_predecessor"` and retried on the next sync.

## Activity Structs

Activities are grouped into structs by responsibility, each with explicit dependencies:

| Struct | Dependencies | Responsibility |
|--------|-------------|----------------|
| `VersionSyncActivities` | Sonatype client, Repository | Fetch versions from Nexus, store new versions |
| `VersionIndexActivities` | Sonatype client, Repository, HTTP client | Fetch/store assets, inspect jars, extract commits |
| `VersionOrderingActivities` | Repository, HTTP client | Schema loading, manifest fetching, ordering, tag storage |
| `ChangelogActivities` | Repository | Fetch versions for enrichment, store enriched commits, store changelogs |
| `GitActivities` | GitCacheManager | Local activities: clone/fetch repos, git show, resolve submodules, git log |

All activity structs are registered on the worker via `w.RegisterActivity(struct)`, which auto-registers all exported methods.

## Retry and Timeout Configuration

| Workflow/Activity | Timeout | Max Attempts | Notes |
|---|---|---|---|
| Sync/Index activities | 30s | 3 | Standard DB + Sonatype API calls |
| Ordering activities | 60s | 3 | ComputeVersionOrdering may process thousands of versions |
| Commit extraction activities | 5m | 2 | Jar downloads can be slow |
| Git clone/fetch (local) | 2m | 3 | Large repos may take time to clone |
| Git read operations (local) | 30s | 3 | git show, git log, git ls-tree |
| Changelog DB activities | 30s | 3 | Standard DB reads/writes |

All use exponential backoff (initial 1-2s, coefficient 2.0, max 30s-1m).

## Batching Strategy

Two batching patterns are used:

**Sliding window** (VersionBatchIndex, ExtractCommitBatch): Bounded concurrency via child workflows with signal-based completion tracking and `ContinueAsNew` pagination. Best for I/O-heavy work (API calls, downloads).

**Sequential chunking** (VersionOrdering): `slices.Chunk(500)` splits data into batches, each dispatched as a separate activity call. Best for batch DB writes where the total dataset is computed in a single pass.

## Running Locally

Prerequisites: Docker (for PostgreSQL via testcontainers), Temporal server (`temporal server start-dev`).

```bash
# Start Temporal dev server
temporal server start-dev

# Run the devtest program (seeds real version data, executes workflows)
go run ./cmd/devtest

# Inspect workflow history at http://localhost:8233
```

The devtest program fetches real version lists from Sonatype for SpongeVanilla (~2,900 versions) and SpongeForge (~3,700 versions), seeds a testcontainers PostgreSQL instance, and executes `VersionOrderingWorkflow` for both artifacts.
