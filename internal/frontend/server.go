package frontend

import (
	"embed"
	"fmt"
	"html/template"
	"io/fs"
	"net/http"

	"github.com/spongepowered/systemofadownload/internal/app"
)

//go:embed static/css/* static/fonts/* static/js/* static/favicon.ico
var staticFS embed.FS

// ServerConfig carries operator-controlled paths into NewServer. All fields
// may be empty for local dev: the server will simply render without a
// sponsor block and not register the /assets/sponsors/ route.
type ServerConfig struct {
	// SponsorsCfgPath is the absolute path to sponsors.json on disk.
	// Typically a Kubernetes ConfigMap subPath mount maintained by the
	// Infrastructure IaC repo.
	SponsorsCfgPath string
	// SponsorsAssetsDir is the directory served at /assets/sponsors/*.
	// Typically a Kubernetes ConfigMap binaryData mount, flat layout,
	// keys matching the bare basenames in sponsors.json.
	SponsorsAssetsDir string
}

// Server holds the frontend HTTP handlers and their dependencies.
type Server struct {
	service           *app.Service
	templates         *Templates
	assets            *AssetManifest
	platforms         []PlatformConfig
	sponsorsJSON      template.JS
	sponsorsAssetsDir string
}

// NewServer creates a new frontend server. Sponsor configuration is loaded
// once at startup; a missing or empty manifest is logged and the server
// renders pages without a sponsor block (required for local dev and for
// first boot before the IaC ConfigMap is applied).
func NewServer(service *app.Service, cfg ServerConfig) (*Server, error) {
	assets, err := BuildAssetManifest(staticFS, "static")
	if err != nil {
		return nil, fmt.Errorf("building asset manifest: %w", err)
	}
	tmpl, err := ParseTemplates(assets)
	if err != nil {
		return nil, fmt.Errorf("parsing templates: %w", err)
	}
	sponsors, err := LoadSponsors(cfg.SponsorsCfgPath)
	if err != nil {
		return nil, fmt.Errorf("loading sponsors: %w", err)
	}
	sponsorsJSON, err := MarshalSponsorsJSON(sponsors)
	if err != nil {
		return nil, fmt.Errorf("marshaling sponsors: %w", err)
	}

	return &Server{
		service:           service,
		templates:         tmpl,
		assets:            assets,
		platforms:         defaultPlatforms(),
		sponsorsJSON:      sponsorsJSON,
		sponsorsAssetsDir: cfg.SponsorsAssetsDir,
	}, nil
}

// RegisterRoutes mounts all frontend routes onto the given mux.
// Specific routes (/healthz, /settings, /assets/) must be registered before
// the catch-all /{project} wildcard to avoid conflicts.
func (s *Server) RegisterRoutes(mux *http.ServeMux, metricsHandler http.Handler) {
	// Static assets (CSS, fonts, picker JS) — embedded in the binary and
	// served under content-hashed URLs so Cache-Control: immutable is
	// safe. Templates must reference these through `{{asset "..."}}`.
	mux.Handle("GET /assets/", s.assets.Handler())

	// Sponsor images live on a writable mount maintained by the
	// Infrastructure IaC repo, separate from the embedded static FS. The
	// more-specific "/assets/sponsors/" pattern wins over the generic
	// "/assets/" prefix in Go 1.22+ ServeMux precedence.
	if s.sponsorsAssetsDir != "" {
		mux.Handle(
			"GET /assets/sponsors/",
			http.StripPrefix("/assets/sponsors/", http.FileServer(http.Dir(s.sponsorsAssetsDir))),
		)
	}

	// Favicon at the conventional root path, served from the embedded static FS.
	staticSub, _ := fs.Sub(staticFS, "static")
	mux.Handle("GET /favicon.ico", http.FileServer(http.FS(staticSub)))

	mux.HandleFunc("GET /healthz", s.handleHealthz)
	mux.Handle("GET /metrics", metricsHandler)
	mux.HandleFunc("GET /settings", s.handleSettings)
	mux.HandleFunc("POST /settings", s.handleSettingsSubmit)
	mux.HandleFunc("GET /{$}", s.handleOverview)
	mux.HandleFunc("GET /{project}", s.handleDownloads)
}

func (s *Server) handleHealthz(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	_, _ = fmt.Fprintln(w, "OK")
}
