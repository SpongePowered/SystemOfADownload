package frontend

import (
	"testing"

	"github.com/spongepowered/systemofadownload/internal/domain"
)

func TestCommitAuthorDisplay(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name     string
		author   *domain.CommitAuthor
		wantName string
		wantLink string
	}{
		{name: "GitHub username preferred", author: &domain.CommitAuthor{Name: "Mona Lisa", GitHubUsername: "octocat"}, wantName: "octocat", wantLink: "https://github.com/octocat"},
		{name: "Git name fallback", author: &domain.CommitAuthor{Name: "Mona Lisa"}, wantName: "Mona Lisa"},
		{name: "unknown author", wantName: "Unknown"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			name, link := commitAuthorDisplay(tt.author)
			if name != tt.wantName || link != tt.wantLink {
				t.Errorf("commitAuthorDisplay() = (%q, %q), want (%q, %q)", name, link, tt.wantName, tt.wantLink)
			}
		})
	}
}
