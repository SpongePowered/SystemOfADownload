package frontend

import (
	"embed"
	"fmt"
	"io/fs"
	"net/http"

	"github.com/spongepowered/systemofadownload/internal/app"
)

//go:embed static/css/* static/fonts/* static/favicon.ico
var staticFS embed.FS

// Server holds the frontend HTTP handlers and their dependencies.
type Server struct {
	service   *app.Service
	templates *Templates
	platforms []PlatformConfig
}

// NewServer creates a new frontend server with hardcoded platform config for now.
func NewServer(service *app.Service) (*Server, error) {
	tmpl, err := ParseTemplates()
	if err != nil {
		return nil, fmt.Errorf("parsing templates: %w", err)
	}

	return &Server{
		service:   service,
		templates: tmpl,
		platforms: defaultPlatforms(),
	}, nil
}

// RegisterRoutes mounts all frontend routes onto the given mux.
// Specific routes (/healthz, /settings, /assets/) must be registered before
// the catch-all /{project} wildcard to avoid conflicts.
func (s *Server) RegisterRoutes(mux *http.ServeMux, metricsHandler http.Handler) {
	// Static assets (CSS, fonts) — embedded in the binary
	staticSub, _ := fs.Sub(staticFS, "static")
	mux.Handle("GET /assets/", http.StripPrefix("/assets/", http.FileServer(http.FS(staticSub))))

	// Favicon at the conventional root path, served from the embedded static FS.
	mux.Handle("GET /favicon.ico", http.FileServer(http.FS(staticSub)))

	mux.HandleFunc("GET /healthz", s.handleHealthz)
	mux.Handle("GET /metrics", metricsHandler)
	mux.HandleFunc("GET /settings", s.handleSettings)
	mux.HandleFunc("GET /{$}", s.handleOverview)
	mux.HandleFunc("GET /{project}", s.handleDownloads)
}

func (s *Server) handleHealthz(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	_, _ = fmt.Fprintln(w, "OK")
}
