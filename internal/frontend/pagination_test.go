package frontend

import (
	"strconv"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
)

// renderPages produces a compact string like "1 2 [3] 4 5 … 20" so test
// expectations read like the buttons a user sees. Active pages are
// bracketed; ellipsis slots render as an ellipsis character.
func renderPages(pages []PageLink) string {
	parts := make([]string, 0, len(pages))
	for _, p := range pages {
		switch {
		case p.Ellipsis:
			parts = append(parts, "…")
		case p.Active:
			parts = append(parts, "["+strconv.Itoa(p.Number)+"]")
		default:
			parts = append(parts, strconv.Itoa(p.Number))
		}
	}
	return strings.Join(parts, " ")
}

func TestBuildPageList(t *testing.T) {
	// Expected outputs cross-referenced against BootstrapVue's
	// <b-pagination-nav first-number last-number> with default limit=5.
	cases := []struct {
		name        string
		currentPage int
		totalPages  int
		want        string
	}{
		{"empty", 1, 0, ""},
		{"single", 1, 1, "[1]"},
		{"two pages", 1, 2, "[1] 2"},
		{"five pages current 1", 1, 5, "[1] 2 3 4 5"},
		{"five pages current 3", 3, 5, "1 2 [3] 4 5"},
		{"six pages current 1", 1, 6, "[1] 2 3 4 5 6"},
		{"six pages current 2", 2, 6, "1 [2] 3 4 5 6"},
		{"six pages current 3", 3, 6, "1 2 [3] 4 5 6"},
		{"six pages current 4", 4, 6, "1 2 3 [4] 5 6"},
		{"six pages current 5", 5, 6, "1 2 3 4 [5] 6"},
		{"six pages current 6", 6, 6, "1 2 3 4 5 [6]"},
		{"ten pages current 1 ellipsis right", 1, 10, "[1] 2 3 4 5 … 10"},
		{"ten pages current 3 ellipsis right", 3, 10, "1 2 [3] 4 5 … 10"},
		{"ten pages current 4 collapses left", 4, 10, "1 2 3 [4] 5 … 10"},
		{"ten pages current 5 both sides", 5, 10, "1 … 4 [5] 6 … 10"},
		{"ten pages current 6 both sides", 6, 10, "1 … 5 [6] 7 … 10"},
		{"ten pages current 7 collapses right", 7, 10, "1 … 6 [7] 8 9 10"},
		{"ten pages current 8 near end", 8, 10, "1 … 6 7 [8] 9 10"},
		{"ten pages current 10 near end", 10, 10, "1 … 6 7 8 9 [10]"},
		{"twenty pages current 1", 1, 20, "[1] 2 3 4 5 … 20"},
		{"twenty pages current 10 middle", 10, 20, "1 … 9 [10] 11 … 20"},
		{"twenty pages current 17 collapses right", 17, 20, "1 … 16 [17] 18 19 20"},
		{"twenty pages current 20", 20, 20, "1 … 16 17 18 19 [20]"},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got := renderPages(buildPageList(tc.currentPage, tc.totalPages))
			assert.Equal(t, tc.want, got)
		})
	}
}

func TestComputePagination(t *testing.T) {
	t.Run("zero total returns empty", func(t *testing.T) {
		p := computePagination(0, 0)
		assert.Equal(t, 0, p.TotalPages)
		assert.Empty(t, p.Pages)
		assert.False(t, p.HasPrev)
		assert.False(t, p.HasNext)
	})

	t.Run("offset drives current page", func(t *testing.T) {
		p := computePagination(40, 200) // page 5 of 20
		assert.Equal(t, 5, p.CurrentPage)
		assert.Equal(t, 20, p.TotalPages)
		assert.Equal(t, "1 … 4 [5] 6 … 20", renderPages(p.Pages))
		assert.True(t, p.HasPrev)
		assert.True(t, p.HasNext)
		assert.Equal(t, 30, p.PrevOffset)
		assert.Equal(t, 50, p.NextOffset)
	})

	t.Run("first page offsets", func(t *testing.T) {
		p := computePagination(0, 200)
		assert.False(t, p.HasPrev)
		assert.True(t, p.HasNext)
		assert.Equal(t, 10, p.NextOffset)
	})

	t.Run("last page offsets", func(t *testing.T) {
		p := computePagination(190, 200)
		assert.True(t, p.HasPrev)
		assert.False(t, p.HasNext)
		assert.Equal(t, 180, p.PrevOffset)
	})

	t.Run("page link offsets use buildsPerPage", func(t *testing.T) {
		p := computePagination(0, 100)
		for _, link := range p.Pages {
			if link.Ellipsis {
				continue
			}
			assert.Equal(t, (link.Number-1)*buildsPerPage, link.Offset)
		}
	})
}
