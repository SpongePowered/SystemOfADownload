package domain

import (
	"fmt"
	"strings"
)

// AssetInfo represents a single asset from a Sonatype Nexus repository search.
type AssetInfo struct {
	DownloadURL string
	Path        string
	ContentType string
	Classifier  string
	Extension   string
	Md5         string
	Sha1        string
	Sha256      string
	Sha512      string
}

// ArtifactTag defines a rule for building tagged metadata on artifact versions.
type ArtifactTag struct {
	Name              string // tag key name
	Regex             string // regex pattern to match against asset classifier/path
	Test              string // tag value to store when the regex matches
	MarkAsRecommended bool   // whether a match marks the version as recommended
}

// CommitInfo holds commit metadata extracted from a jar's META-INF.
// After enrichment, additional fields are populated from git operations.
type CommitInfo struct {
	Sha        string `json:"sha"`
	Repository string `json:"repository,omitempty"`
	Branch     string `json:"branch,omitempty"`
	URL        string `json:"url,omitempty"` // GitHub commit URL

	// Enrichment fields (populated by CommitEnrichmentWorkflow)
	Message    string            `json:"message,omitempty"`
	Body       string            `json:"body,omitempty"`
	Author     *CommitAuthor     `json:"author,omitempty"`
	CommitDate string            `json:"commitDate,omitempty"`
	Submodules []SubmoduleCommit `json:"submodules,omitempty"`
	Changelog  *Changelog        `json:"changelog,omitempty"`
	EnrichedAt string            `json:"enrichedAt,omitempty"`

	// ChangelogStatus tracks pending changelog computation.
	// "pending_predecessor" means N-1 was not yet enriched.
	ChangelogStatus string `json:"changelogStatus,omitempty"`
}

// CommitAuthor holds the name and email of a git commit author.
type CommitAuthor struct {
	Name  string `json:"name"`
	Email string `json:"email"`
}

// SubmoduleCommit holds commit details for a submodule at a specific version.
type SubmoduleCommit struct {
	Repository string        `json:"repository"`
	Sha        string        `json:"sha"`
	URL        string        `json:"url,omitempty"`
	Message    string        `json:"message,omitempty"`
	Author     *CommitAuthor `json:"author,omitempty"`
	CommitDate string        `json:"commitDate,omitempty"`
}

// Changelog holds the list of commits between two consecutive versions.
type Changelog struct {
	PreviousVersion     string                `json:"previousVersion"`
	Commits             []CommitSummary       `json:"commits"`
	SubmoduleChangelogs map[string]*Changelog `json:"submoduleChangelogs,omitempty"`
}

// CommitSummary is a condensed commit record used in changelogs.
type CommitSummary struct {
	Sha        string        `json:"sha"`
	URL        string        `json:"url,omitempty"`
	Message    string        `json:"message"`
	Author     *CommitAuthor `json:"author,omitempty"`
	CommitDate string        `json:"commitDate,omitempty"`
}

// CommitURL builds a GitHub commit URL from a repository URL and SHA.
func CommitURL(repoURL, sha string) string {
	base := strings.TrimRight(repoURL, "/")
	base = strings.TrimSuffix(base, ".git")
	return fmt.Sprintf("%s/commit/%s", base, sha)
}
