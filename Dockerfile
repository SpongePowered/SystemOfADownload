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
    go build -ldflags "-s -w -X main.Version=${VERSION}" -o /out/worker ./cmd/worker

# --- App image (default target) ---
FROM alpine:3.23 AS app

RUN apk add --no-cache ca-certificates git tzdata

COPY --from=builder /out/server /app/server
COPY --from=builder /out/worker /app/worker

ENTRYPOINT ["/app/server"]

# --- Migrate image (built with --target migrate) ---
FROM migrate/migrate:v4.19.1 AS migrate-bin

FROM alpine:3.23 AS migrate

RUN apk add --no-cache ca-certificates

COPY --from=migrate-bin /usr/local/bin/migrate /usr/local/bin/migrate
COPY db/migrations /migrations

ENTRYPOINT ["migrate", "-path", "/migrations"]
