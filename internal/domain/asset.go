package domain

// AssetInfo represents a single asset from a Sonatype Nexus repository search.
type AssetInfo struct {
	DownloadURL string
	Path        string
	ContentType string
	Classifier  string
	Extension   string
	Sha256      string
}

// ArtifactTag defines a rule for building tagged metadata on artifact versions.
type ArtifactTag struct {
	Name              string // tag key name
	Regex             string // regex pattern to match against asset classifier/path
	Test              string // tag value to store when the regex matches
	MarkAsRecommended bool   // whether a match marks the version as recommended
}

// CommitInfo holds commit metadata extracted from a jar's META-INF.
type CommitInfo struct {
	Sha        string
	Repository string
}
