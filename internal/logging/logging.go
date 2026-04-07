// Package logging configures structured logging for all SOAD services.
//
// Console output is always JSON (for k8s / Loki ingestion).
// When LOG_FILE is set, a human-readable text log is also written to that path.
// LOG_LEVEL controls the minimum level (debug, info, warn, error); default is info.
package logging

import (
	"fmt"
	"io"
	"log/slog"
	"os"
	"strings"

	slogmulti "github.com/samber/slog-multi"
	"go.uber.org/fx/fxevent"
)

// Result holds the handler and closer returned by Setup.
type Result struct {
	Handler slog.Handler
	Closer  io.Closer
}

// Setup builds a slog.Handler that fans out to:
//   - JSON on stderr (always)
//   - Text to LOG_FILE (when set)
//
// It also calls slog.SetDefault so that all subsequent slog calls
// (including fx startup) use the configured handler immediately.
// The returned Result.Closer must be closed on shutdown to flush the
// log file (it is a no-op when no file is configured).
func Setup() (*Result, error) {
	level := parseLevel(os.Getenv("LOG_LEVEL"))
	opts := &slog.HandlerOptions{Level: level}

	jsonHandler := slog.NewJSONHandler(os.Stderr, opts)

	logFile := os.Getenv("LOG_FILE")
	if logFile == "" {
		r := &Result{Handler: jsonHandler, Closer: io.NopCloser(nil)}
		slog.SetDefault(slog.New(r.Handler))
		return r, nil
	}

	f, err := os.OpenFile(logFile, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o600) //nolint:gosec // log file path from trusted env var
	if err != nil {
		return nil, fmt.Errorf("opening log file %s: %w", logFile, err)
	}

	textHandler := slog.NewTextHandler(f, opts)
	fanout := slogmulti.Fanout(jsonHandler, textHandler)

	r := &Result{Handler: fanout, Closer: f}
	slog.SetDefault(slog.New(r.Handler))
	return r, nil
}

// FxLogger returns an fxevent.Logger that routes fx lifecycle events
// through the global slog logger.
func FxLogger() fxevent.Logger {
	return &fxevent.SlogLogger{Logger: slog.Default()}
}

func parseLevel(s string) slog.Level {
	switch strings.ToLower(strings.TrimSpace(s)) {
	case "debug":
		return slog.LevelDebug
	case "warn", "warning":
		return slog.LevelWarn
	case "error":
		return slog.LevelError
	default:
		return slog.LevelInfo
	}
}
