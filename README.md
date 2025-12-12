# SystemOfADownload

The metadata generator webapp that serves up enhanced information
and "tagging" for artifacts from a Maven repository. This is intended to
serve as a backend to power the data to generate for serving an enhanced
downloads website.

## Requirements

Using [Mise](https://mise.jdx.dev/), the `mise.toml` file defines tool requirements, notably
Go 1.25 as a minimum.

### Deployment

Being a webapp, there's two distinct binaries that run:
- `server` - the web api server
- `worker` - the background worker that does the indexing and maintenance


## Project layout (DDD + TDD-friendly)

This repo uses a lightweight Domain-Driven Design structure with pure domain types, an application layer, and adapters for delivery (HTTP) and background workers.

Directory overview:

- `internal/domain` — Pure domain entities and logic (no framework deps)
- `internal/app` — Application services/use cases orchestrating domain logic
- `internal/httpapi` — HTTP adapter and routing for the server binary
- `internal/worker` — Background worker orchestration
- `cmd/server` — Entrypoint for the HTTP API server
- `cmd/worker` — Entrypoint for the background worker

Testing: standard library `testing` and `net/http/httptest` are used, no external test deps.

Run tests:

```
go test ./...
```

Build binaries:

```
go build -o bin/server ./cmd/server
go build -o bin/worker ./cmd/worker
```

Run locally:

```
./bin/server   # serves HTTP on :8080
./bin/worker   # runs background loop
```

HTTP API quickstart:

- Health check: `GET http://localhost:8080/healthz` → 200 OK
- Metadata generation (example):

```
curl -sS -X POST http://localhost:8080/v1/metadata \
  -H 'Content-Type: application/json' \
  -d '{"group_id":"org.example","artifact_id":"demo","version":"1.0.0","tags":["Latest","  stable  "]}'
```

Response:

```
{
  "coordinates": "org.example:demo:1.0.0",
  "tags": ["latest","stable"],
  "is_stable": true
}
```


