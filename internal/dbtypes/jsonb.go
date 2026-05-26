// Package dbtypes holds Go types referenced by sqlc column overrides.
// Living outside internal/db avoids the import cycle that would otherwise
// occur (generated code can't import the repository layer).
package dbtypes

// VersionAsset matches one element of the jsonb_agg(jsonb_build_object(...))
// payload produced by db/query.sql:GetVersionDetailRaw.
type VersionAsset struct {
	Classifier  string `json:"classifier"`
	DownloadURL string `json:"download_url"`
	Md5         string `json:"md5"`
	Sha1        string `json:"sha1"`
	Sha256      string `json:"sha256"`
	Sha512      string `json:"sha512"`
	Extension   string `json:"extension"`
}

// VersionAssets is the slice form. pgx's JSONB codec falls through to
// json.Unmarshal when the destination isn't *[]byte / BytesScanner, so a
// plain typed slice scans directly with no Scanner method required.
type VersionAssets []VersionAsset

// VersionTagMap is the destination for jsonb_object_agg(tag_key, tag_value).
// Same story: pgx's JSONB codec runs json.Unmarshal into it.
type VersionTagMap map[string]string
