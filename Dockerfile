FROM golang:1.26-alpine AS builder

RUN apk add --no-cache git ca-certificates

WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download

COPY . .

ARG VERSION=dev
RUN CGO_ENABLED=0 go build -ldflags "-s -w -X main.Version=${VERSION}" -o /out/server ./cmd/server && \
    CGO_ENABLED=0 go build -ldflags "-s -w -X main.Version=${VERSION}" -o /out/worker ./cmd/worker

FROM alpine:3.22

RUN apk add --no-cache ca-certificates git tzdata

COPY --from=builder /out/server /app/server
COPY --from=builder /out/worker /app/worker

ENTRYPOINT ["/app/server"]
