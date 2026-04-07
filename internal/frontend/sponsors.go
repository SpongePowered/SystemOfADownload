package frontend

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"html/template"
	"log/slog"
	"os"
	"strings"
)

// Sponsor is a single sponsor entry loaded from sponsors.json.
//
// The schema mirrors the legacy SpongeDownloads SPA so existing IaC-managed
// manifests stay compatible: name, images[], link, additionalText, weight.
type Sponsor struct {
	Name           string         `json:"name"`
	Images         []SponsorImage `json:"images"`
	Link           string         `json:"link"`
	AdditionalText string         `json:"additionalText"`
	Weight         int            `json:"weight"`
}

// SponsorImage is one source in a sponsor's <picture> rendering. Media and
// Type are optional and let a manifest opt into responsive art direction.
type SponsorImage struct {
	Src   string `json:"src"`
	Media string `json:"media,omitempty"`
	Type  string `json:"type,omitempty"`
}

// LoadSponsors reads and validates a sponsor manifest from the given path.
//
// An empty path or a missing file returns (nil, nil) without error so the
// frontend renders without a sponsor block during local dev and during the
// first boot before the IaC-managed ConfigMap has been applied.
//
// Entries that fail validation are dropped with a warning rather than
// failing the whole load: a single bad sponsor should not take down the
// page. The function only returns an error when the file exists but cannot
// be read or parsed at all.
func LoadSponsors(path string) ([]Sponsor, error) {
	if path == "" {
		slog.Info("sponsor manifest path not configured; rendering without sponsors")
		return nil, nil
	}
	data, err := os.ReadFile(path) //nolint:gosec // path comes from an operator-controlled env var
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			slog.Warn("sponsor manifest not found; rendering without sponsors", "path", path)
			return nil, nil
		}
		return nil, fmt.Errorf("reading sponsor manifest %q: %w", path, err)
	}
	var raw []Sponsor
	if err := json.Unmarshal(data, &raw); err != nil {
		return nil, fmt.Errorf("parsing sponsor manifest %q: %w", path, err)
	}

	valid := make([]Sponsor, 0, len(raw))
	for i := range raw {
		if reason := validateSponsor(&raw[i]); reason != "" {
			slog.Warn("dropping invalid sponsor entry",
				"name", raw[i].Name, "reason", reason, "path", path)
			continue
		}
		valid = append(valid, raw[i])
	}
	slog.Info("sponsor manifest loaded",
		"path", path, "loaded", len(valid), "dropped", len(raw)-len(valid))
	return valid, nil
}

// validateSponsor returns an empty string when the entry is acceptable, or a
// short human-readable reason describing why it must be dropped.
func validateSponsor(s *Sponsor) string {
	if s.Name == "" {
		return "missing name"
	}
	if s.Link == "" {
		return "missing link"
	}
	if s.Weight <= 0 {
		return "weight must be a positive integer"
	}
	if len(s.Images) == 0 {
		return "no images"
	}
	for _, img := range s.Images {
		if img.Src == "" {
			return "image src is empty"
		}
		// ConfigMap keys cannot contain '/', and the on-disk mount is flat;
		// reject any nested path so /assets/sponsors/<src> always resolves
		// against SPONSORS_ASSETS_DIR without escaping it.
		if strings.ContainsAny(img.Src, `/\`) {
			return "image src must be a bare basename (no path separators)"
		}
	}
	return ""
}

// MarshalSponsorsJSON serializes the sponsor list for inline injection into
// the layout via <script type="application/json">. Returns an empty
// template.JS when the list is empty so the layout can suppress the script
// tags entirely with a simple {{if .Sponsors}}.
//
// Returned bytes are HTML-safe: encoding/json escapes '<', '>', and '&' to
// their \u00xx forms by default, so a hostile manifest cannot break out of
// the script element with a literal "</script>" sequence.
func MarshalSponsorsJSON(sponsors []Sponsor) (template.JS, error) {
	if len(sponsors) == 0 {
		return "", nil
	}
	var buf bytes.Buffer
	enc := json.NewEncoder(&buf)
	enc.SetEscapeHTML(true)
	if err := enc.Encode(sponsors); err != nil {
		return "", fmt.Errorf("marshaling sponsors: %w", err)
	}
	// Encoder appends a trailing newline; trim for cleaner HTML output.
	return template.JS(bytes.TrimRight(buf.Bytes(), "\n")), nil //nolint:gosec // HTML-escaped JSON, safe in script context
}
