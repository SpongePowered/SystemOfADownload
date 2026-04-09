package frontend

import (
	"net/http"
	"net/http/httptest"
	"regexp"
	"strings"
	"testing"
	"testing/fstest"
)

// fakeAssets builds a minimal embed-shaped MapFS with the shape
// BuildAssetManifest expects: a `static/` root containing css/fonts/js
// subdirectories and a favicon. Using a MapFS keeps the tests hermetic
// and independent of the real embedded bytes.
func fakeAssets() fstest.MapFS {
	return fstest.MapFS{
		"static/css/fonts.css": {Data: []byte(
			"@font-face { src: url('/assets/fonts/lato-400.woff2') format('woff2'); }\n" +
				"@font-face { src: url(\"/assets/fonts/lato-700.woff2\") format('woff2'); }\n",
		)},
		"static/css/main.css": {Data: []byte(
			".bg { background: url('https://example.com/image.jpg'); }\n" +
				".sponsor { background: url('/assets/sponsors/logo.png'); }\n",
		)},
		"static/fonts/lato-400.woff2": {Data: []byte("FAKE-LATO-400")},
		"static/fonts/lato-700.woff2": {Data: []byte("FAKE-LATO-700")},
		"static/js/sponsors.js":       {Data: []byte("console.log('sponsors');\n")},
		"static/favicon.ico":          {Data: []byte("ICO")},
	}
}

// hashedRE matches the filename shape produced by AssetManifest.add:
// <stem>.<8 hex chars><ext>. Used to assert URL shape without pinning
// the exact digest, which would break whenever the fake bytes change.
var hashedRE = regexp.MustCompile(`^(.*)\.([0-9a-f]{8})(\.[a-z0-9]+)$`)

func mustBuildManifest(t *testing.T) *AssetManifest {
	t.Helper()
	m, err := BuildAssetManifest(fakeAssets(), "static")
	if err != nil {
		t.Fatalf("BuildAssetManifest: %v", err)
	}
	return m
}

func TestBuildAssetManifest_HashedFilenameShape(t *testing.T) {
	m := mustBuildManifest(t)

	logical := "/assets/fonts/lato-400.woff2"
	hashed := m.URL(logical)
	if hashed == logical {
		t.Fatalf("expected %q to be rewritten, got unchanged", logical)
	}
	// Expect: /assets/fonts/lato-400.<digest>.woff2
	base := hashed[strings.LastIndex(hashed, "/")+1:]
	if !hashedRE.MatchString(base) {
		t.Fatalf("hashed basename %q does not match <stem>.<hash>.<ext>", base)
	}
	if !strings.HasPrefix(base, "lato-400.") || !strings.HasSuffix(base, ".woff2") {
		t.Fatalf("hashed URL %q lost its stem or extension", hashed)
	}
}

func TestBuildAssetManifest_Deterministic(t *testing.T) {
	// Two builds from identical input must produce identical URLs. This
	// is the invariant a CDN depends on — if the hash drifted between
	// process restarts of the same binary, clients would redownload.
	a, err := BuildAssetManifest(fakeAssets(), "static")
	if err != nil {
		t.Fatalf("build a: %v", err)
	}
	b, err := BuildAssetManifest(fakeAssets(), "static")
	if err != nil {
		t.Fatalf("build b: %v", err)
	}
	for logical, ua := range a.urls {
		if ub := b.urls[logical]; ub != ua {
			t.Errorf("drift for %q: %q vs %q", logical, ua, ub)
		}
	}
}

func TestBuildAssetManifest_CSSRewritesLocalFontRefs(t *testing.T) {
	m := mustBuildManifest(t)

	// fonts.css should have been rewritten so that its `url(...)`
	// references point at the hashed woff2 URLs, then hashed itself.
	cssURL := m.URL("/assets/css/fonts.css")
	entry, ok := m.entries[cssURL]
	if !ok {
		t.Fatalf("fonts.css missing from manifest entries (hashed URL %q)", cssURL)
	}
	body := string(entry.body)

	for _, font := range []string{"/assets/fonts/lato-400.woff2", "/assets/fonts/lato-700.woff2"} {
		if strings.Contains(body, font+"'") || strings.Contains(body, font+"\"") {
			t.Errorf("fonts.css still contains unhashed %q", font)
		}
		hashed := m.URL(font)
		if !strings.Contains(body, hashed) {
			t.Errorf("fonts.css missing hashed reference to %q (expected %q)", font, hashed)
		}
	}
}

func TestBuildAssetManifest_CSSLeavesExternalURLsAlone(t *testing.T) {
	m := mustBuildManifest(t)

	mainCSS := m.URL("/assets/css/main.css")
	body := string(m.entries[mainCSS].body)

	// External URL must survive unchanged.
	if !strings.Contains(body, "https://example.com/image.jpg") {
		t.Error("main.css lost its external https url")
	}
	// /assets/sponsors/* is served from a separate writable mount and is
	// not in the manifest; refs to it must be left alone rather than
	// eagerly rewritten to a dead hashed URL.
	if !strings.Contains(body, "/assets/sponsors/logo.png") {
		t.Error("main.css dropped /assets/sponsors/ reference")
	}
}

func TestBuildAssetManifest_CSSFileHashChangesWhenFontChanges(t *testing.T) {
	// The whole point of pass 2: changing a font's bytes must cascade
	// to a new hashed URL for any CSS that references it. Otherwise
	// clients would hold a stale fonts.css pointing at an old font URL.
	base := fakeAssets()
	mBase, err := BuildAssetManifest(base, "static")
	if err != nil {
		t.Fatalf("base build: %v", err)
	}

	modified := fakeAssets()
	modified["static/fonts/lato-400.woff2"] = &fstest.MapFile{Data: []byte("DIFFERENT-LATO-400-BYTES")}
	mMod, err := BuildAssetManifest(modified, "static")
	if err != nil {
		t.Fatalf("modified build: %v", err)
	}

	if mBase.URL("/assets/fonts/lato-400.woff2") == mMod.URL("/assets/fonts/lato-400.woff2") {
		t.Fatal("font URL did not change when font bytes changed")
	}
	if mBase.URL("/assets/css/fonts.css") == mMod.URL("/assets/css/fonts.css") {
		t.Fatal("fonts.css URL did not change when a referenced font changed — cascade broken")
	}
	// lato-700 was untouched; its URL should be stable.
	if mBase.URL("/assets/fonts/lato-700.woff2") != mMod.URL("/assets/fonts/lato-700.woff2") {
		t.Error("lato-700 URL drifted despite unchanged bytes")
	}
}

func TestAssetManifest_URL_UnknownPath(t *testing.T) {
	m := mustBuildManifest(t)
	// Missing entries fall through to the logical path so a template
	// typo surfaces as a 404 at request time rather than a startup crash.
	if got := m.URL("/assets/does/not/exist.png"); got != "/assets/does/not/exist.png" {
		t.Errorf("unknown path: got %q, want passthrough", got)
	}
}

func TestAssetManifest_Handler_ServesHashedURL(t *testing.T) {
	m := mustBuildManifest(t)
	h := m.Handler()

	hashed := m.URL("/assets/fonts/lato-400.woff2")
	req := httptest.NewRequest(http.MethodGet, hashed, http.NoBody)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status: got %d, want 200", rec.Code)
	}
	if got := rec.Header().Get("Cache-Control"); got != assetCacheControl {
		t.Errorf("Cache-Control: got %q, want %q", got, assetCacheControl)
	}
	if got := rec.Header().Get("Content-Type"); got != "font/woff2" {
		t.Errorf("Content-Type: got %q, want font/woff2", got)
	}
	if etag := rec.Header().Get("ETag"); etag == "" {
		t.Error("missing ETag")
	}
	if rec.Body.String() != "FAKE-LATO-400" {
		t.Errorf("body: got %q, want %q", rec.Body.String(), "FAKE-LATO-400")
	}
}

func TestAssetManifest_Handler_NotFound(t *testing.T) {
	m := mustBuildManifest(t)
	h := m.Handler()

	req := httptest.NewRequest(http.MethodGet, "/assets/nope.woff2", http.NoBody)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusNotFound {
		t.Fatalf("status: got %d, want 404", rec.Code)
	}
}

func TestAssetManifest_Handler_IfNoneMatch(t *testing.T) {
	m := mustBuildManifest(t)
	h := m.Handler()

	hashed := m.URL("/assets/fonts/lato-400.woff2")

	// First fetch to learn the ETag.
	first := httptest.NewRecorder()
	h.ServeHTTP(first, httptest.NewRequest(http.MethodGet, hashed, http.NoBody))
	etag := first.Header().Get("ETag")
	if etag == "" {
		t.Fatal("no ETag on first response")
	}

	// Second fetch with matching If-None-Match should 304 with empty body.
	req := httptest.NewRequest(http.MethodGet, hashed, http.NoBody)
	req.Header.Set("If-None-Match", etag)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusNotModified {
		t.Fatalf("status: got %d, want 304", rec.Code)
	}
	if rec.Body.Len() != 0 {
		t.Errorf("304 response should have empty body, got %d bytes", rec.Body.Len())
	}
}

func TestAssetManifest_Handler_Head(t *testing.T) {
	m := mustBuildManifest(t)
	h := m.Handler()

	hashed := m.URL("/assets/fonts/lato-400.woff2")
	req := httptest.NewRequest(http.MethodHead, hashed, http.NoBody)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status: got %d, want 200", rec.Code)
	}
	if rec.Body.Len() != 0 {
		t.Errorf("HEAD response should have empty body, got %d bytes", rec.Body.Len())
	}
	// 13 = byte length of the fake lato-400 payload.
	if got := rec.Header().Get("Content-Length"); got != "13" {
		t.Errorf("Content-Length: got %q, want 13", got)
	}
}

func TestBuildAssetManifest_RealEmbedFS(t *testing.T) {
	// Sanity check against the actual embedded static tree: every file
	// ParseTemplates/layout.gohtml references through `asset` must be
	// resolvable to a hashed URL that the handler actually serves.
	m, err := BuildAssetManifest(staticFS, "static")
	if err != nil {
		t.Fatalf("BuildAssetManifest on real embed FS: %v", err)
	}

	required := []string{
		"/assets/css/fonts.css",
		"/assets/css/main.css",
		"/assets/js/sponsors.js",
		"/assets/fonts/lato-400.woff2",
		"/assets/fonts/lato-400i.woff2",
		"/assets/fonts/lato-700.woff2",
		"/assets/fonts/montserrat-latin.woff2",
	}
	h := m.Handler()
	for _, logical := range required {
		hashed := m.URL(logical)
		if hashed == logical {
			t.Errorf("%s was not rewritten by the manifest", logical)
			continue
		}
		req := httptest.NewRequest(http.MethodGet, hashed, http.NoBody)
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, req)
		if rec.Code != http.StatusOK {
			t.Errorf("GET %s -> %d, want 200", hashed, rec.Code)
		}
		if rec.Body.Len() == 0 {
			t.Errorf("GET %s served zero bytes — this is exactly the Cloudflare bug the manifest is meant to prevent", hashed)
		}
	}

	// fonts.css should end up referencing hashed font URLs, not raw ones.
	cssBody := string(m.entries[m.URL("/assets/css/fonts.css")].body)
	if strings.Contains(cssBody, "/assets/fonts/lato-400.woff2'") ||
		strings.Contains(cssBody, "/assets/fonts/lato-400.woff2\"") {
		t.Error("fonts.css still contains unhashed lato-400.woff2 reference")
	}
}
