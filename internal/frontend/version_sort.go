package frontend

import "strings"

// IsPreRelease returns true if the version string contains a hyphen,
// indicating a pre-release MC version (e.g., "1.21-pre1", "1.21-rc1").
// Used to filter the MC version dropdown when the user hasn't enabled
// pre-release display.
func IsPreRelease(version string) bool {
	return strings.Contains(version, "-")
}
