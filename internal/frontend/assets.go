package frontend

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io/fs"
	"mime"
	"net/http"
	"path"
	"regexp"
	"sort"
	"strings"
)

// hashLen is the number of hex characters of the SHA-256 digest that we
// splice into asset filenames. 8 hex = 32 bits, collision-resistant for
// the handful of files in the embedded static tree.
const hashLen = 8

// assetCacheControl is applied to every manifest entry. Because the URL
// itself changes when the content changes, a 1-year immutable TTL is
// safe: stale bytes can never be served under a URL that a client
// previously fetched.
const assetCacheControl = "public, max-age=31536000, immutable"

// assetEntry holds the pre-computed response for one manifest URL.
type assetEntry struct {
	body        []byte
	contentType string
	etag        string
}

// AssetManifest maps logical asset paths (as they appear in templates and
// CSS, e.g. "/assets/css/fonts.css") to content-hashed URLs, and holds
// the bytes served at each hashed URL. It is built once at startup from
// an embed.FS and is read-only afterwards.
type AssetManifest struct {
	urls    map[string]string     // logical path -> hashed URL
	entries map[string]assetEntry // hashed URL -> served payload
}

// cssLocalURLRE matches `url(...)` references in CSS whose target is a
// site-local `/assets/...` path. Quotes are optional (single or double).
// External http(s) URLs are left alone — we only rewrite things we can
// actually resolve in the manifest.
var cssLocalURLRE = regexp.MustCompile(`url\(\s*(['"]?)(/assets/[^'")\s]+)(['"]?)\s*\)`)

// mimeOverrides pins Content-Type for extensions whose mappings vary by
// platform mime.types. Deterministic headers matter for CDN caching.
var mimeOverrides = map[string]string{
	".css":   "text/css; charset=utf-8",
	".js":    "text/javascript; charset=utf-8",
	".woff2": "font/woff2",
	".woff":  "font/woff",
	".ico":   "image/vnd.microsoft.icon",
	".svg":   "image/svg+xml",
	".json":  "application/json",
}

// BuildAssetManifest walks fsys under root and produces a manifest with
// content-hashed URLs. CSS files are rewritten in two passes so that a
// stylesheet's `url(...)` references point at the hashed URLs of the
// fonts/images they link to.
func BuildAssetManifest(fsys fs.FS, root string) (*AssetManifest, error) {
	m := &AssetManifest{
		urls:    make(map[string]string),
		entries: make(map[string]assetEntry),
	}

	// Collect every file once; we need deterministic ordering so two
	// equivalent builds produce identical manifests.
	type fileRec struct {
		path    string // path inside fsys (e.g. "static/css/fonts.css")
		logical string // URL the templates use (e.g. "/assets/css/fonts.css")
	}
	var files []fileRec
	if err := fs.WalkDir(fsys, root, func(p string, d fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if d.IsDir() {
			return nil
		}
		rel := strings.TrimPrefix(p, root+"/")
		files = append(files, fileRec{
			path:    p,
			logical: "/assets/" + rel,
		})
		return nil
	}); err != nil {
		return nil, fmt.Errorf("walking %s: %w", root, err)
	}
	sort.Slice(files, func(i, j int) bool { return files[i].path < files[j].path })

	// Pass 1: hash every non-CSS asset. CSS is deferred so that the
	// rewrite step in pass 2 has hashed URLs to substitute in.
	for _, f := range files {
		if strings.EqualFold(path.Ext(f.path), ".css") {
			continue
		}
		body, err := fs.ReadFile(fsys, f.path)
		if err != nil {
			return nil, fmt.Errorf("reading %s: %w", f.path, err)
		}
		m.add(f.logical, body)
	}

	// Pass 2: rewrite CSS `url(...)` references that point into the
	// manifest, then hash the rewritten bytes.
	for _, f := range files {
		if !strings.EqualFold(path.Ext(f.path), ".css") {
			continue
		}
		body, err := fs.ReadFile(fsys, f.path)
		if err != nil {
			return nil, fmt.Errorf("reading %s: %w", f.path, err)
		}
		rewritten := cssLocalURLRE.ReplaceAllFunc(body, func(match []byte) []byte {
			parts := cssLocalURLRE.FindSubmatch(match)
			if len(parts) < 4 {
				return match
			}
			hashed, ok := m.urls[string(parts[2])]
			if !ok {
				// Not in the manifest (e.g. /assets/sponsors/* on the
				// writable ConfigMap mount, or a typo). Leave the
				// reference alone rather than breaking the page.
				return match
			}
			var buf bytes.Buffer
			buf.WriteString("url(")
			buf.Write(parts[1])
			buf.WriteString(hashed)
			buf.Write(parts[3])
			buf.WriteString(")")
			return buf.Bytes()
		})
		m.add(f.logical, rewritten)
	}

	return m, nil
}

// add computes a hashed URL for logical and stores body under it.
func (m *AssetManifest) add(logical string, body []byte) {
	sum := sha256.Sum256(body)
	digest := hex.EncodeToString(sum[:])[:hashLen]

	// Splice the digest before the extension so the MIME type derived
	// from the extension stays correct: fonts.css -> fonts.<d>.css.
	dir, file := path.Split(logical)
	ext := path.Ext(file)
	stem := strings.TrimSuffix(file, ext)
	hashed := dir + stem + "." + digest + ext

	m.urls[logical] = hashed
	m.entries[hashed] = assetEntry{
		body:        body,
		contentType: contentTypeFor(ext, body),
		etag:        `"` + digest + `"`,
	}
}

// contentTypeFor picks a Content-Type for an asset. The override table
// wins so headers are deterministic across platforms; mime.TypeByExtension
// and http.DetectContentType are fallbacks.
func contentTypeFor(ext string, body []byte) string {
	if ct, ok := mimeOverrides[strings.ToLower(ext)]; ok {
		return ct
	}
	if ct := mime.TypeByExtension(ext); ct != "" {
		return ct
	}
	return http.DetectContentType(body)
}

// URL returns the hashed URL for logical, or the logical path unchanged
// if it's not in the manifest. Returning the input instead of panicking
// means a missing asset surfaces as a 404 at request time, not a startup
// crash — useful while templates and the embed glob drift.
func (m *AssetManifest) URL(logical string) string {
	if h, ok := m.urls[logical]; ok {
		return h
	}
	return logical
}

// Handler serves manifest entries with long-lived immutable caching.
// Unknown paths under /assets/ return 404 — mount more-specific handlers
// (e.g. /assets/sponsors/) before this one if they need to take priority,
// although Go 1.22+ ServeMux precedence handles that automatically.
func (m *AssetManifest) Handler() http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		entry, ok := m.entries[r.URL.Path]
		if !ok {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Cache-Control", assetCacheControl)
		w.Header().Set("ETag", entry.etag)
		if match := r.Header.Get("If-None-Match"); match != "" && match == entry.etag {
			w.WriteHeader(http.StatusNotModified)
			return
		}
		w.Header().Set("Content-Type", entry.contentType)
		w.Header().Set("Content-Length", fmt.Sprintf("%d", len(entry.body)))
		if r.Method == http.MethodHead {
			w.WriteHeader(http.StatusOK)
			return
		}
		_, _ = w.Write(entry.body)
	})
}
