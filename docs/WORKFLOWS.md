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

## Worker Deployment Versioning

The worker uses Temporal's [Worker Deployment Versioning](https://docs.temporal.io/workers#worker-versioning) to enable safe rolling updates. Each worker binary identifies itself with a deployment name and build ID, allowing Temporal to route workflows to compatible workers.

**Configuration (env vars):**
- `BUILD_ID` (required) — image tag or version identifier, set by the deployment pipeline. The worker will refuse to start if this is empty.
- `POD_NAME` (optional) — Kubernetes pod name, used as the Temporal client `Identity` for traceability in the Temporal UI.

**Worker registration:**
```go
worker.Options{
    DeploymentOptions: worker.DeploymentOptions{
        UseVersioning: true,
        Version: worker.WorkerDeploymentVersion{
            DeploymentName: "soad-worker",
            BuildID:        cfg.BuildID,
        },
        DefaultVersioningBehavior: workflow.VersioningBehaviorAutoUpgrade,
    },
}
```

`AutoUpgrade` means in-flight workflows automatically move to the new build after `set-current-version` promotes it — no manual pinning needed.

**Promotion flow:** After deployment, an ArgoCD PostSync Job promotes the new build:
```
temporal worker deployment set-current-version \
    --deployment-name=soad-worker \
    --build-id=$BUILD_ID
```

The `DeploymentName` ("soad-worker") must match the `--deployment-name` in the PostSync job. The worker self-registers with Temporal on startup; the PostSync job then tells Temporal to route new workflow tasks to this build.

## Workflow Details

### VersionSyncWorkflow

The top-level orchestrator. Two Temporal Schedules are created per registered artifact:

| Schedule | Interval | Source | Purpose |
|----------|----------|--------|---------|
| `version-sync-fast-{g}-{a}` | 2m | `metadata` | Low-latency new-version detection via `maven-metadata.xml` |
| `version-sync-full-{g}-{a}` | 1h (±10m jitter) | `search`   | Correctness backstop via Sonatype REST component search |

Both schedules run the same `VersionSyncWorkflow`, differing only in the `Source` field they pass. The workflow dispatches `FetchVersions` on that field.

**Input:** `VersionSyncInput{GroupID, ArtifactID, ForceReindex, ForceChangelog, Source}`

Empty `Source` defaults to `search` for backward compatibility with any schedule actions still in flight at rollout.

**Steps:**
1. Fetch candidate versions from Sonatype.
   - `metadata`: one GET to `maven-metadata.xml` + per-new-candidate `VersionHasAssets` probe. The probe gates out versions whose POMs are listed in the metadata but whose assets aren't yet indexed (or whose only copies live in a `SONATYPE_REPO_DENY`-denied hosted repo). Without this gate the silent empty-assets return in `VersionIndexWorkflow` would leave permanent zero-asset ghost rows.
   - `search`: paginates `/v3/search` components. Sonatype indexes components after assets, so no probe is needed.
2. Compare against DB and store only new versions.
3. Launch batch indexing for new versions (assets + commits) — or **all** versions when `ForceReindex` is true.
4. Compute version ordering and extract schema-driven tags for **all** versions.

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

## Periodic Version Sync (Temporal Schedules)

When an artifact is registered, **two** Temporal Schedules are created to periodically poll Sonatype. The pair splits the latency goal (fast) from the correctness goal (complete).

**Fast schedule** — `version-sync-fast-{groupID}-{artifactID}`
- **Interval:** every 2 minutes with 15s jitter
- **Source:** `metadata` — one GET to `maven-metadata.xml` plus an asset-presence probe for each candidate new version
- **Overlap policy:** `BUFFER_ONE` — if a sync is running when the next tick fires, one run is queued and starts after the current finishes
- **Trigger immediately:** yes — the first sync runs at registration time

**Full schedule** — `version-sync-full-{groupID}-{artifactID}`
- **Interval:** every 1 hour with 10-minute jitter (spreads REST-API load across artifacts)
- **Source:** `search` — paginates the REST component-search API (authoritative list)
- **Overlap policy:** `SKIP` — if an hourly tick fires while the previous hourly run is still going, drop the tick. The fast path is already covering latency.

**Workflow execution timeout:** 30 minutes on both schedules. Bounds a wedged parent.

**Concurrency model:** each schedule auto-generates unique workflow IDs per fire (schedule ID + timestamp). Cross-schedule concurrent runs are possible but rare (jitter windows of 15s and 10m). Idempotency against concurrent runs relies on:
- `UNIQUE(artifact_id, version)` in the `artifact_versions` table
- The pre-insert "already stored" filter inside `StoreNewVersions`
- The metadata path's probe gate (ensures we don't race REST discovery to create a ghost row)

**Legacy schedule ID:** before the dual-schedule rollout, schedules were named `version-sync-{groupID}-{artifactID}`. Existing namespaces need the temporal CLI commands in the runbook section below to convert.

## On-Demand Sync Trigger

The `POST /groups/{groupID}/artifacts/{artifactID}/sync` endpoint triggers a schedule immediately. The query parameter `source` chooses which schedule to fire:

- `?source=metadata` (default when omitted) — fires the fast schedule. Use after publishing a new version to Sonatype so SOAD indexes it right away.
- `?source=search` — fires the full REST-pagination schedule. Use when `maven-metadata.xml` is suspected stale and you want to force the correctness backstop immediately.

**Auth:** Requires the same `ADMIN_API_TOKEN` bearer token as other write operations.

**Response codes:**
- `200` — sync triggered successfully
- `404` — no schedule found (artifact may not be registered)
- `500` — Temporal unreachable or internal error

**How it works:** The endpoint calls `ScheduleHandle.Trigger()` on the existing schedule. The schedule's `BUFFER_ONE` overlap policy means rapid repeated triggers are safe — at most one extra run is queued.

### Reusable GitHub Action

A reusable workflow at `.github/workflows/trigger-sync.yml` allows other SpongePowered repos to trigger a sync after publishing artifacts:

```yaml
jobs:
  notify-soad:
    uses: SpongePowered/SystemOfADownload/.github/workflows/trigger-sync.yml@dev
    with:
      group-id: org.spongepowered
      artifact-id: spongeapi
      api-url: https://dl-api.spongepowered.org
    secrets:
      admin-token: ${{ secrets.SOAD_ADMIN_TOKEN }}
```

**Setup for consuming repos:**
1. Add a `SOAD_ADMIN_TOKEN` organization secret (or per-repo secret) containing the SOAD `ADMIN_API_TOKEN` value.
2. Add the `uses:` job above to your release/publish workflow, after the step that pushes artifacts to Sonatype.

**Note:** Sonatype replication may take a few seconds. If the sync runs before the artifact is visible in Sonatype, the next scheduled poll (within 2 minutes) will catch it.

## Workflow Conflict Policies

| Trigger | Workflow | Conflict Policy | Behavior |
|---------|----------|----------------|----------|
| Fast schedule (2m) | VersionSyncWorkflow | `BUFFER_ONE` | Queue one run if current sync still running |
| Full schedule (1h) | VersionSyncWorkflow | `SKIP` | Drop the tick if a previous full run is still going |
| `POST .../sync` | VersionSyncWorkflow | — | Fires target schedule (default: fast; `?source=search` for full) |
| `PutArtifactSchema` | VersionOrderingWorkflow | `TERMINATE_EXISTING` | Cancel stale run, restart with new schema |

The `PutArtifactSchema` trigger uses `ALLOW_DUPLICATE` reuse policy so workflows can be re-run after completion.

## Migrating Legacy Schedules

Namespaces that predate the dual-schedule rollout still have a single `version-sync-{g}-{a}` schedule per artifact. Convert each artifact with the temporal CLI: create the fast + full pair first, then delete the legacy schedule (same create-before-delete ordering the code uses, so at every moment the artifact has at least one schedule firing).

Substitute the correct `<group>`, `<artifact>`, `<namespace>`, and `<host>` values and run per artifact:

```bash
# 1. Fast: 2m metadata
temporal schedule create \
  --schedule-id "version-sync-fast-<group>-<artifact>" \
  --workflow-type VersionSyncWorkflow \
  --task-queue version-sync \
  --interval 2m --jitter 15s \
  --overlap-policy BufferOne \
  --execution-timeout 30m \
  --input '{"GroupID":"<group>","ArtifactID":"<artifact>","Source":"metadata"}' \
  --namespace <namespace> --address <host>

# 2. Full: 1h search
temporal schedule create \
  --schedule-id "version-sync-full-<group>-<artifact>" \
  --workflow-type VersionSyncWorkflow \
  --task-queue version-sync \
  --interval 1h --jitter 10m \
  --overlap-policy Skip \
  --execution-timeout 30m \
  --input '{"GroupID":"<group>","ArtifactID":"<artifact>","Source":"search"}' \
  --namespace <namespace> --address <host>

# 3. Delete legacy (only after the two above succeed)
temporal schedule delete \
  --schedule-id "version-sync-<group>-<artifact>" \
  --namespace <namespace> --address <host>
```

Notes:
- `--workflow-type VersionSyncWorkflow` must match the name the worker registers — confirm in the Temporal UI under Task Queues → `version-sync` → Workflows before running.
- `--input` field names must match Go struct capitalization (`GroupID`, `ArtifactID`, `Source`) since the SDK uses the default JSON payload converter.
- If your `temporal` CLI version doesn't support `--trigger-immediately` on `schedule create`, run `temporal schedule trigger --schedule-id version-sync-fast-<g>-<a> --namespace <ns>` right after creation to kick off the first sync; otherwise the first fast tick arrives within 2 minutes.
- Don't trigger the full schedule on rollout — staggering its first fire via jitter avoids all artifacts hammering Sonatype at once.

**Rollback:** recreate the legacy schedule (same shape as the fast schedule but with `--schedule-id "version-sync-<g>-<a>"` and omit the `"Source"` field from `--input`; the activity defaults to `search`, matching pre-feature behavior), then `temporal schedule delete` the fast and full ones.

**Partial-failure recovery:** if a create succeeds but a subsequent delete fails (Temporal blip mid-run), the artifact ends up with both the legacy and the new pair firing until the delete is retried. Harmless — the extra legacy run does the same work as one of the new ones; idempotency at the DB layer absorbs the overlap. Re-run the `temporal schedule delete` for the legacy ID when convenient.

## Search Attributes

`VersionSyncWorkflow` upserts two custom search attributes on each run:

- `VersionSyncSource` (keyword) — `search` or `metadata`, the source actually dispatched.
- `ArtifactCoordinate` (keyword) — `<groupID>:<artifactID>`, for one-click filtering in the Temporal UI.

These must be registered in the namespace before first use:

```
temporal operator search-attribute create --name VersionSyncSource --type Keyword
temporal operator search-attribute create --name ArtifactCoordinate --type Keyword
```

If unregistered, `UpsertTypedSearchAttributes` logs a warning and the workflow continues normally.

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

Prerequisites: Docker (for PostgreSQL), Temporal server (`temporal server start-dev`). Bring up a local Postgres with the migrations in `db/migrations/` applied, point `DATABASE_URL` at it, then run `go run ./cmd/worker` against `temporal server start-dev`. Register an artifact via the HTTP API to create schedules and kick off the pipeline; inspect workflow history at `http://localhost:8233`.
