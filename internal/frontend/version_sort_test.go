package frontend

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestIsPreRelease(t *testing.T) {
	// Hyphen-suffixed pre-releases.
	assert.True(t, IsPreRelease("1.21-pre1"))
	assert.True(t, IsPreRelease("1.21-rc1"))
	assert.True(t, IsPreRelease("24w10a-snapshot"))
	assert.True(t, IsPreRelease("26.1-snapshot-6"))
	// Bare weekly snapshots (no hyphen).
	assert.True(t, IsPreRelease("25w14a"))
	assert.True(t, IsPreRelease("24w45b"))
	assert.True(t, IsPreRelease("25w02c"))
	// Stable releases.
	assert.False(t, IsPreRelease("1.21"))
	assert.False(t, IsPreRelease("1.21.4"))
	assert.False(t, IsPreRelease("1.12.2"))
	assert.False(t, IsPreRelease("26.1.1"))
}
