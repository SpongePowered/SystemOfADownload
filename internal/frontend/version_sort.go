package frontend

import (
	"regexp"
	"strings"
)

// weeklySnapshotRe matches bare Minecraft weekly snapshot version strings
// like "25w14a" or "24w45b". These don't contain a hyphen, so the naive
// contains-"-" check doesn't catch them — they'd leak into the version
// selector dropdown even when the user has pre-release display disabled.
//
// Kept in sync with `weeklySnapshotRe` in internal/domain/version_ordering.go
// and the `\d+w\d+[a-z]` branch of `mcPattern` in the domain tests.
var weeklySnapshotRe = regexp.MustCompile(`^\d+w\d+[a-z]$`)

// IsPreRelease returns true if the version string represents a pre-release
// Minecraft version that should be hidden from the selector dropdown unless
// the user has opted in via the `prerelease` preference. Three formats are
// recognised, matching what the domain version parser accepts:
//
//   - Any version containing a hyphen: `1.21-pre1`, `1.21-rc1`,
//     `26.1-snapshot-6`, `24w10a-snapshot`.
//   - Bare weekly snapshots: `25w14a`, `24w45b`.
func IsPreRelease(version string) bool {
	if strings.Contains(version, "-") {
		return true
	}
	return weeklySnapshotRe.MatchString(version)
}
