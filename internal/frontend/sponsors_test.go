package frontend

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestLoadSponsors_EmptyPath(t *testing.T) {
	got, err := LoadSponsors("")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got != nil {
		t.Fatalf("expected nil sponsors for empty path, got %v", got)
	}
}

func TestLoadSponsors_MissingFile(t *testing.T) {
	got, err := LoadSponsors(filepath.Join(t.TempDir(), "does-not-exist.json"))
	if err != nil {
		t.Fatalf("missing file should not error: %v", err)
	}
	if got != nil {
		t.Fatalf("expected nil sponsors for missing file, got %v", got)
	}
}

func TestLoadSponsors_InvalidJSON(t *testing.T) {
	path := filepath.Join(t.TempDir(), "sponsors.json")
	writeFile(t, path, "{not json")
	if _, err := LoadSponsors(path); err == nil {
		t.Fatal("expected parse error")
	}
}

func TestLoadSponsors_ValidEntries(t *testing.T) {
	path := filepath.Join(t.TempDir(), "sponsors.json")
	writeFile(t, path, `[
		{"name":"A","images":[{"src":"a.svg"}],"link":"https://a","additionalText":"","weight":3},
		{"name":"B","images":[{"src":"b.png"}],"link":"https://b","additionalText":"hi","weight":1}
	]`)
	got, err := LoadSponsors(path)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(got) != 2 {
		t.Fatalf("expected 2 sponsors, got %d", len(got))
	}
	if got[0].Name != "A" || got[0].Weight != 3 || got[0].Images[0].Src != "a.svg" {
		t.Errorf("first entry malformed: %+v", got[0])
	}
}

func TestLoadSponsors_DropsInvalidEntries(t *testing.T) {
	path := filepath.Join(t.TempDir(), "sponsors.json")
	writeFile(t, path, `[
		{"name":"good","images":[{"src":"good.svg"}],"link":"https://g","weight":1},
		{"name":"","images":[{"src":"x.svg"}],"link":"https://x","weight":1},
		{"name":"no-link","images":[{"src":"y.svg"}],"link":"","weight":1},
		{"name":"zero-weight","images":[{"src":"z.svg"}],"link":"https://z","weight":0},
		{"name":"no-images","images":[],"link":"https://n","weight":1},
		{"name":"empty-src","images":[{"src":""}],"link":"https://e","weight":1},
		{"name":"nested-path","images":[{"src":"sub/dir.svg"}],"link":"https://p","weight":1},
		{"name":"backslash-path","images":[{"src":"a\\b.svg"}],"link":"https://b","weight":1}
	]`)
	got, err := LoadSponsors(path)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(got) != 1 || got[0].Name != "good" {
		t.Fatalf("expected only the 'good' entry to survive validation, got %v", got)
	}
}

func TestMarshalSponsorsJSON_Empty(t *testing.T) {
	out, err := MarshalSponsorsJSON(nil)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if out != "" {
		t.Fatalf("expected empty output for nil sponsors, got %q", out)
	}
}

func TestMarshalSponsorsJSON_HTMLEscaped(t *testing.T) {
	// Manifests with hostile content must not break out of the surrounding
	// <script type="application/json"> element. encoding/json escapes
	// '<', '>', and '&' by default; verify the output reflects that.
	in := []Sponsor{{
		Name:           "X",
		Images:         []SponsorImage{{Src: "x.svg"}},
		Link:           "https://x",
		AdditionalText: "</script><script>alert(1)</script>",
		Weight:         1,
	}}
	out, err := MarshalSponsorsJSON(in)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if strings.Contains(string(out), "</script>") {
		t.Fatalf("output must not contain literal </script>: %s", out)
	}
	if !strings.Contains(string(out), `\u003c`) {
		t.Fatalf("expected HTML-escaped angle brackets in output: %s", out)
	}
}

func writeFile(t *testing.T, path, content string) {
	t.Helper()
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatalf("writing fixture: %v", err)
	}
}
