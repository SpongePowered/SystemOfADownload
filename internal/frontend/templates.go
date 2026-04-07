package frontend

import (
	"embed"
	"fmt"
	"html/template"
	"io"
	"time"
)

//go:embed templates/*.gohtml
var templateFS embed.FS

// Templates holds the parsed HTML templates.
type Templates struct {
	overview  *template.Template
	downloads *template.Template
}

// templateFuncs returns the shared template function map.
func templateFuncs() template.FuncMap {
	return template.FuncMap{
		"currentYear": func() int { return time.Now().Year() },
		"subtract":    func(a, b int) int { return a - b },
	}
}

// ParseTemplates parses all embedded templates with the shared layout.
func ParseTemplates() (*Templates, error) {
	parse := func(name string) (*template.Template, error) {
		t, err := template.New("").Funcs(templateFuncs()).ParseFS(
			templateFS, "templates/layout.gohtml", "templates/"+name,
		)
		if err != nil {
			return nil, fmt.Errorf("parsing %s: %w", name, err)
		}
		return t, nil
	}

	overview, err := parse("overview.gohtml")
	if err != nil {
		return nil, err
	}

	downloads, err := parse("downloads.gohtml")
	if err != nil {
		return nil, err
	}

	return &Templates{
		overview:  overview,
		downloads: downloads,
	}, nil
}

// PageData is the base context passed to every page template.
// The layout template uses Platforms and Year; page-specific templates use Page.
type PageData struct {
	Platforms []PlatformConfig
	Year      int
	Page      any
	ActiveNav string // path segment: "" for overview, platform ID, or "settings"
}

// RenderOverview renders the overview page.
func (t *Templates) RenderOverview(w io.Writer, data PageData) error {
	return t.overview.ExecuteTemplate(w, "layout", data)
}

// RenderDownloads renders the downloads page.
func (t *Templates) RenderDownloads(w io.Writer, data PageData) error {
	return t.downloads.ExecuteTemplate(w, "layout", data)
}
