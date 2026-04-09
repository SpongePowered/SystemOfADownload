FROM --platform=$BUILDPLATFORM golang:1.26-alpine AS builder

RUN apk add --no-cache git ca-certificates

WORKDIR /src
COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    go mod download

COPY . .

ARG VERSION=dev
ARG TARGETOS TARGETARCH
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH \
    go build -ldflags "-s -w -X main.Version=${VERSION}" -o /out/server ./cmd/server && \
    CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH \
    go build -ldflags "-s -w -X main.Version=${VERSION}" -o /out/worker ./cmd/worker && \
    CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH \
    go build -ldflags "-s -w -X main.Version=${VERSION}" -o /out/frontend ./cmd/frontend

# --- App image (default target) ---
FROM alpine:3.23 AS app

# tini is PID 1 in every container using this image so orphaned grandchildren
# (e.g. git subprocesses forked by git clone/fetch) get reaped instead of
# accumulating as zombies. The Go runtime does not reap orphans it didn't
# directly exec — only tini/dumb-init/a real init will. See cf0fcba for the
# direct-child half of the same problem.
RUN apk add --no-cache ca-certificates git tini tzdata

COPY --from=builder /out/server /app/server
COPY --from=builder /out/worker /app/worker
COPY --from=builder /out/frontend /app/frontend

ENTRYPOINT ["/app/server"]

# --- Migrate image (built with --target migrate) ---
FROM migrate/migrate:v4.19.1 AS migrate-bin

FROM alpine:3.23 AS migrate

RUN apk add --no-cache ca-certificates

COPY --from=migrate-bin /usr/local/bin/migrate /usr/local/bin/migrate
COPY db/migrations /migrations

ENTRYPOINT ["migrate", "-path", "/migrations"]
