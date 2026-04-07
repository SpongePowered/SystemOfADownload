package frontend

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestIsPreRelease(t *testing.T) {
	assert.True(t, IsPreRelease("1.21-pre1"))
	assert.True(t, IsPreRelease("1.21-rc1"))
	assert.True(t, IsPreRelease("24w10a-snapshot"))
	assert.False(t, IsPreRelease("1.21"))
	assert.False(t, IsPreRelease("1.21.4"))
	assert.False(t, IsPreRelease("1.12.2"))
}
