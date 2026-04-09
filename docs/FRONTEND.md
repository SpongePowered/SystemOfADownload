# SOAD Frontend: Server-Side Rendered Downloads

This document describes the design and implementation of the Go server-side rendered
frontend that replaces the EOL Vue 2 SPA, deployed as a separate binary (`cmd/frontend`).

## Architecture: Separate Binary

The frontend runs as `cmd/frontend`, alongside `cmd/server` (API) and `cmd/worker`
(Temporal). All three share internal packages but have independent lifecycles.

**Shared packages:** `internal/app.Service`, `internal/repository`, `internal/otelsetup`,
`internal/domain`.

```
cmd/
  server/main.go    — API server (JSON, OpenAPI)
  worker/main.go    — Temporal worker
  frontend/main.go  — SSR frontend (HTML)
```

---

## Current Implementation Status

### Done (Phase 0 + Phase 1)

**Binary & wiring:**
- `cmd/frontend/main.go` — uber/fx DI, DB pool with startup ping check, OTel, HTTP server
- Default port 8090, configurable via `PORT` env var
- `DATABASE_URL` env var (required, validated at startup)

**Package layout:**
```
internal/frontend/
  server.go           — route registration, static file serving
  assets.go           — content-hashed asset manifest (embed FS → hashed URLs, CSS url() cascade, immutable-safe handler)
  config.go           — PlatformConfig, ArtifactType (hardcoded, YAML loading planned)
  handlers.go         — handleOverview, handleDownloads, handleSettings(+Submit)
  preferences.go      — cookie-backed prerelease/apifilter preferences
  templates.go        — embedded template parsing, PageData, render methods
  classifiers.go      — legacy vs modern classifier selection, asset matching
  commits.go          — commit body deduplication, relative time, experimental detection
  version_sort.go     — IsPreRelease filter (DB handles sort order)
  version_sort_test.go
  static/css/main.css — 380-line stylesheet with CSS custom property palette
  templates/
    layout.gohtml     — base HTML: topbar, header, content block, footer
    overview.gohtml   — platform cards grid
    downloads.gohtml  — version selector, builds, commits, pagination
    settings.gohtml   — preference form (prerelease + apifilter checkboxes)
```

**Routes:**
| Route | Handler | Status |
|---|---|---|
| `GET /` | `handleOverview` | Done |
| `GET /{project}` | `handleDownloads` | Done |
| `GET /settings` | `handleSettings` | Done |
| `POST /settings` | `handleSettingsSubmit` | Done |
| `GET /assets/*` | Content-hashed static file server (immutable) | Done |
| `GET /healthz` | Health check | Done |
| `GET /metrics` | Prometheus | Done |

**Downloads page features:**
- Platform logo with colored badge ("Sponge [Vanilla]")
- Split-button version selector (yellow + dark caret dropdown)
- Recommended build section (grey background, full-size buttons, no commits)
- All-builds list (float layout, alternating row backgrounds, btn-sm buttons)
- Classifier selection: legacy vs modern based on MC version prefix
- Experimental detection (`0.0-rc` or `snapshot` in version string)
- Commit changelog with underlined links to GitHub commits
- Commit body deduplication (body == message → clear; startsWith → strip)
- Head commit body applied to first changelog entry
- Commit URL generation from `CommitInfo.Repository` + SHA (fallback for older data)
- Submodule changelogs (indented, bordered, with repo name headers)
- Show/collapse older commits (initial 5, expand/collapse toggle)
- Three changelog states: complete, processing (with warning), no changelog
- Pagination (offset-based, grey buttons, active state)
- FontAwesome 6 icons via CDN (navbar dropdown, chevron)

**CSS:**
- 380 lines with `--sponge-*`, `--grey-*`, `--clr-*` variable palette
- Matches Bootstrap 4 Flatly production appearance
- Lato body font, Montserrat headings (Google Fonts)
- Responsive breakpoints: 576/768/992/1200px

**What the DB handles (not reimplemented):**
- Version sorting — `sort_order` column, `ORDER BY av.sort_order DESC`
- Tag list ordering — `ListDistinctTagsByArtifact` returns `ORDER BY MAX(sort_order) DESC`

---

## Remaining Work

### Phase 2: Config + Preferences
- [ ] YAML platform config loader (replace hardcoded Go structs in `config.go`)
- [x] Query param preferences (`prerelease=1`, `apifilter=0`) + cookie defaults
- [x] Settings page (form with two toggles, cookie set + redirect)
- [x] Query modifiers (API version forcing per MC version)
- [x] Pre-release MC version filtering in dropdown

### Phase 3: Sponsors
- [x] Read sponsor manifest from `SPONSORS_CONFIG_PATH` at startup (JSON matching legacy `SpongeDownloads/sponsors.json` shape: `name`, `images[]`, `link`, `additionalText`, `weight`)
- [x] Graceful fallback when `SPONSORS_CONFIG_PATH` is unset or the file is missing/invalid — render pages without a sponsor block, log a warning, do not crash. Required for local dev and first boot before IaC applies.
- [x] Serve sponsor images from `SPONSORS_ASSETS_DIR` at `/assets/sponsors/*` (separate from the embedded `/assets/css/*` and `/assets/fonts/*` routes)
- [x] Reject non-basename image paths in the manifest (must resolve flat against `SPONSORS_ASSETS_DIR`)
- [x] Inject sponsor list into the page as `<script type="application/json">` for client-side weighted-random selection (CDN-friendly)
- [x] Client-side sponsor picker JS with `<picture>/<source>` rendering and `additionalText`
- [ ] Quiet `/assets/sponsors/*` in the request log alongside `/healthz` and `/metrics`
- [ ] Cache-Control headers per route (sponsors: `public, max-age=3600`) — owned by Traefik IngressRoute, not the binary

**Ownership:** `sponsors.json` and the sponsor image files live in the **Infrastructure IaC repo**, not in this repo. They are delivered to the pod as read-only mounts (ConfigMap today) driven by IaC. Updates are a PR to the IaC repo followed by a rolling restart of the frontend Deployment — **no SIGHUP or fsnotify reload is implemented or required**. Do not commit sponsor JSON or imagery to this repo; do not add a placeholder manifest.

### Phase 4: Production Readiness
- [ ] Dockerfile update (third binary build)
- [ ] Integration tests (httptest with test DB)
- [ ] Template rendering tests
- [ ] Classifier selection tests (legacy cutoff boundary)
- [ ] OTel span instrumentation on handlers
- [ ] In-memory cache for tag lists (5-min TTL)
- [ ] Documentation updates (docs/WORKFLOWS.md, README)

---

## Configuration

### Platform Config (currently hardcoded)

All three platforms share the same artifact types, query modifiers, and legacy prefixes:

```go
ArtifactTypes: []ArtifactType{
    {Name: "Download", ClassifierModern: "universal", ClassifierLegacy: "", Primary: true},
    {Name: "Sources", ClassifierModern: "sources-dev", ClassifierLegacy: "sources"},
    {Name: "Dev", ClassifierModern: "", ClassifierLegacy: "dev-shaded"},
}
```

Legacy classifier selection is prefix-based: MC versions starting with `1.8.`, `1.9.`,
`1.10.`, `1.11.`, or `1.12.` use legacy classifiers.

### Sponsor Config (planned)

JSON format matching the existing `sponsors.json`. Selection is client-side (weighted
random per pageview) to work with CDN caching.

### Caching Strategy

Cloudflare CDN with Cache-Control headers. All page state in URL (no `Vary: Cookie`).
Downloads pages: 2-min TTL. Overview: 5-min TTL.

**Embedded assets (`/assets/css/*`, `/assets/fonts/*`, `/assets/js/*`, and
any other file under `internal/frontend/static/`):** served at content-hashed
URLs by `AssetManifest` (`internal/frontend/assets.go`). At startup the
manifest walks the embed FS in two passes: pass 1 hashes every non-CSS file
and rewrites its URL from `foo.woff2` to `foo.<8-hex>.woff2`; pass 2 runs a
regex over each CSS file, substitutes hashed URLs for any site-local
`url(/assets/...)` reference it can resolve, then hashes the rewritten
bytes. The cascade means changing a font automatically bumps the URL of
every CSS file that references it, so a template never points at stale
bytes. Templates call the hashed URL through the `{{asset "..."}}` funcmap
entry; the handler serves with `Cache-Control: public, max-age=31536000,
immutable`, a strong ETag, pinned Content-Type (deterministic across
platform mime.types), and supports `If-None-Match` / HEAD. External
`https://...` URLs inside CSS are left alone. `/assets/sponsors/*` is a
separate writable mount — its URLs are not rewritten.

Because every byte change produces a new URL, Cloudflare never serves
stale bytes under a URL a client previously fetched. One-time CF purges
are only needed when migrating from an earlier non-hashed state.

### User Preferences

Two preferences are supported, matching the legacy SPA:

| Name | Cookie / query | Default | Effect |
|---|---|---|---|
| `prerelease` | `prerelease=1\|0` | `0` (off) | Show pre-release MC versions (containing `-`) in the selector dropdown |
| `apifilter` | `apifilter=1\|0` | `1` (on) | Apply platform `QueryModifiers` (e.g., `1.12.2` → `api=7`) so early API prototypes are hidden |

Resolution order per request: query parameter → cookie → package default.
Query parameters are honored but not required — there is no automatic
redirect to a "canonical" URL. The `/settings` form writes cookies via a
`POST` → `303 See Other` → `GET /settings?saved=1` (PRG pattern) so a
browser refresh after save does not re-submit the form.

Unchecked checkboxes are absent from the submitted form, so submitting
always rewrites both cookies — the stored value mirrors exactly what the
user sees in the UI.

---

## Deployment

```
[Cloudflare] → [Reverse Proxy] → :8080  cmd/server   (API, /v2/)
                                → :8090  cmd/frontend (SSR, /)
                                → :xxxx  cmd/worker   (Temporal)
```

Dockerfile adds third binary:
```dockerfile
go build -o /out/frontend ./cmd/frontend
COPY --from=builder /out/frontend /app/frontend
```

---

## What This Does NOT Include

- **Authentication/authorization** — read-only, public-facing
- **Tag display badges** — wired in SPA but produces zero output; only Recommended/Experimental
- **Search, build comparison** — not in scope
- **Client-side JS framework** — ~30 lines inline JS total
- **API changes** — reads from same DB, no new endpoints
- **Download proxying** — tracked separately
