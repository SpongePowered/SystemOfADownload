FROM golang:1.26-alpine AS builder

RUN apk add --no-cache git ca-certificates

WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download

COPY . .

ARG VERSION=dev
RUN CGO_ENABLED=0 go build -ldflags "-s -w -X main.Version=${VERSION}" -o /out/server ./cmd/server && \
    CGO_ENABLED=0 go build -ldflags "-s -w -X main.Version=${VERSION}" -o /out/worker ./cmd/worker

# --- App image (default target) ---
FROM alpine:3.22 AS app

RUN apk add --no-cache ca-certificates git tzdata

COPY --from=builder /out/server /app/server
COPY --from=builder /out/worker /app/worker

ENTRYPOINT ["/app/server"]

# --- Migrate image (built with --target migrate) ---
FROM migrate/migrate:v4.18.3 AS migrate-bin

FROM alpine:3.22 AS migrate

RUN apk add --no-cache ca-certificates

COPY --from=migrate-bin /usr/local/bin/migrate /usr/local/bin/migrate
COPY db/migrations /migrations

ENTRYPOINT ["migrate", "-path", "/migrations"]
