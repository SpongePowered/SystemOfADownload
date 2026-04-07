package frontend

import (
	"fmt"
	"math"
	"strings"
	"time"
)

// CommitEntry represents a single commit for template rendering.
type CommitEntry struct {
	Message      string
	Body         string
	Author       string
	RelativeTime string
	AbsoluteTime string
	Link         string
	HasBody      bool
}

// deduplicateCommitBody applies the SPA's deduplication logic:
//   - if body == message, clear body
//   - if body starts with message, strip the message prefix from body
func deduplicateCommitBody(message, body string) string {
	if body == "" || body == message {
		return ""
	}
	if strings.HasPrefix(body, message) {
		trimmed := strings.TrimSpace(body[len(message):])
		return trimmed
	}
	return body
}

// relativeTime formats a time as a human-readable relative string.
func relativeTime(t time.Time) string {
	d := time.Since(t)

	switch {
	case d < time.Minute:
		return "just now"
	case d < time.Hour:
		n := int(math.Round(d.Minutes()))
		if n == 1 {
			return "1 minute ago"
		}
		return fmt.Sprintf("%d minutes ago", n)
	case d < 24*time.Hour:
		n := int(math.Round(d.Hours()))
		if n == 1 {
			return "1 hour ago"
		}
		return fmt.Sprintf("%d hours ago", n)
	case d < 30*24*time.Hour:
		n := int(math.Round(d.Hours() / 24))
		if n == 1 {
			return "1 day ago"
		}
		return fmt.Sprintf("%d days ago", n)
	case d < 365*24*time.Hour:
		n := int(math.Round(d.Hours() / (24 * 30)))
		if n == 1 {
			return "1 month ago"
		}
		return fmt.Sprintf("%d months ago", n)
	default:
		n := int(math.Round(d.Hours() / (24 * 365)))
		if n == 1 {
			return "1 year ago"
		}
		return fmt.Sprintf("%d years ago", n)
	}
}

// isExperimental returns true if the version string indicates an experimental build.
// Matches the SPA's logic: version contains "0.0-rc" or "snapshot" (case-insensitive).
func isExperimental(version string) bool {
	v := strings.ToLower(version)
	return strings.Contains(v, "0.0-rc") || strings.Contains(v, "snapshot")
}
