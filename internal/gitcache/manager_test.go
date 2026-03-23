package gitcache

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"testing"
)

// setupTestRepo creates a temporary git repo with a few commits and returns its path.
func setupTestRepo(t *testing.T) string {
	t.Helper()
	dir := t.TempDir()
	repo := filepath.Join(dir, "test-repo")

	run := func(args ...string) {
		t.Helper()
		cmd := exec.Command("git", append([]string{"-C", repo}, args...)...)
		cmd.Env = append(os.Environ(),
			"GIT_AUTHOR_NAME=Test",
			"GIT_AUTHOR_EMAIL=test@example.com",
			"GIT_COMMITTER_NAME=Test",
			"GIT_COMMITTER_EMAIL=test@example.com",
		)
		out, err := cmd.CombinedOutput()
		if err != nil {
			t.Fatalf("git %v failed: %v\n%s", args, err, out)
		}
	}

	if err := os.MkdirAll(repo, 0o755); err != nil {
		t.Fatal(err)
	}

	run("init")
	run("checkout", "-b", "main")

	// First commit
	if err := os.WriteFile(filepath.Join(repo, "file.txt"), []byte("hello"), 0o644); err != nil {
		t.Fatal(err)
	}
	run("add", "file.txt")
	run("commit", "-m", "Initial commit")

	// Second commit
	if err := os.WriteFile(filepath.Join(repo, "file.txt"), []byte("world"), 0o644); err != nil {
		t.Fatal(err)
	}
	run("add", "file.txt")
	run("commit", "-m", "Second commit")

	// Third commit
	if err := os.WriteFile(filepath.Join(repo, "file.txt"), []byte("!"), 0o644); err != nil {
		t.Fatal(err)
	}
	run("add", "file.txt")
	run("commit", "-m", "Third commit")

	return repo
}

// getCommitSHA returns the SHA of a commit by ref.
func getCommitSHA(t *testing.T, repo, ref string) string {
	t.Helper()
	cmd := exec.Command("git", "-C", repo, "rev-parse", ref)
	out, err := cmd.Output()
	if err != nil {
		t.Fatalf("rev-parse %s: %v", ref, err)
	}
	return string(out[:len(out)-1]) // trim newline
}

func TestRepoPath(t *testing.T) {
	m := NewManager("/tmp/test-cache")

	tests := []struct {
		url      string
		wantSuffix string
	}{
		{"https://github.com/SpongePowered/Sponge", "github.com/SpongePowered/Sponge.git"},
		{"https://github.com/SpongePowered/Sponge.git", "github.com/SpongePowered/Sponge.git"},
	}
	for _, tt := range tests {
		got, err := m.repoPath(tt.url)
		if err != nil {
			t.Fatalf("repoPath(%q) error: %v", tt.url, err)
		}
		if !filepath.IsAbs(got) {
			t.Errorf("expected absolute path, got %s", got)
		}
		if want := filepath.Join("/tmp/test-cache", tt.wantSuffix); got != want {
			t.Errorf("repoPath(%q) = %s, want %s", tt.url, got, want)
		}
	}
}

func TestEnsureClonedAndFetch(t *testing.T) {
	sourceRepo := setupTestRepo(t)
	cacheDir := t.TempDir()
	m := NewManager(cacheDir)
	ctx := context.Background()

	// First call: clone
	path, err := m.EnsureCloned(ctx, sourceRepo)
	if err != nil {
		t.Fatalf("EnsureCloned (clone): %v", err)
	}

	// Verify it's a bare repo
	if _, err := os.Stat(filepath.Join(path, "HEAD")); err != nil {
		t.Fatalf("expected HEAD in bare repo at %s: %v", path, err)
	}

	// Second call: fetch (should succeed without error)
	path2, err := m.EnsureCloned(ctx, sourceRepo)
	if err != nil {
		t.Fatalf("EnsureCloned (fetch): %v", err)
	}
	if path != path2 {
		t.Errorf("paths differ: %s vs %s", path, path2)
	}
}

func TestGetCommitDetails(t *testing.T) {
	sourceRepo := setupTestRepo(t)
	cacheDir := t.TempDir()
	m := NewManager(cacheDir)
	ctx := context.Background()

	path, err := m.EnsureCloned(ctx, sourceRepo)
	if err != nil {
		t.Fatalf("EnsureCloned: %v", err)
	}

	sha := getCommitSHA(t, sourceRepo, "HEAD")

	details, err := m.GetCommitDetails(ctx, path, sha)
	if err != nil {
		t.Fatalf("GetCommitDetails: %v", err)
	}

	if details.Sha != sha {
		t.Errorf("SHA = %s, want %s", details.Sha, sha)
	}
	if details.Message != "Third commit" {
		t.Errorf("Message = %q, want %q", details.Message, "Third commit")
	}
	if details.AuthorName != "Test" {
		t.Errorf("AuthorName = %q, want %q", details.AuthorName, "Test")
	}
	if details.AuthorEmail != "test@example.com" {
		t.Errorf("AuthorEmail = %q, want %q", details.AuthorEmail, "test@example.com")
	}
	if details.CommitDate == "" {
		t.Error("CommitDate is empty")
	}
}

func TestComputeChangelog(t *testing.T) {
	sourceRepo := setupTestRepo(t)
	cacheDir := t.TempDir()
	m := NewManager(cacheDir)
	ctx := context.Background()

	path, err := m.EnsureCloned(ctx, sourceRepo)
	if err != nil {
		t.Fatalf("EnsureCloned: %v", err)
	}

	firstSHA := getCommitSHA(t, sourceRepo, "HEAD~2")
	headSHA := getCommitSHA(t, sourceRepo, "HEAD")

	commits, err := m.ComputeChangelog(ctx, path, firstSHA, headSHA)
	if err != nil {
		t.Fatalf("ComputeChangelog: %v", err)
	}

	// Should have 2 commits (second and third, excluding first)
	if len(commits) != 2 {
		t.Fatalf("expected 2 commits, got %d", len(commits))
	}

	// Commits are in reverse chronological order
	if commits[0].Message != "Third commit" {
		t.Errorf("commits[0].Message = %q, want %q", commits[0].Message, "Third commit")
	}
	if commits[1].Message != "Second commit" {
		t.Errorf("commits[1].Message = %q, want %q", commits[1].Message, "Second commit")
	}
}

func TestComputeChangelogEmpty(t *testing.T) {
	sourceRepo := setupTestRepo(t)
	cacheDir := t.TempDir()
	m := NewManager(cacheDir)
	ctx := context.Background()

	path, err := m.EnsureCloned(ctx, sourceRepo)
	if err != nil {
		t.Fatalf("EnsureCloned: %v", err)
	}

	headSHA := getCommitSHA(t, sourceRepo, "HEAD")

	// Same SHA → empty changelog
	commits, err := m.ComputeChangelog(ctx, path, headSHA, headSHA)
	if err != nil {
		t.Fatalf("ComputeChangelog: %v", err)
	}
	if len(commits) != 0 {
		t.Errorf("expected 0 commits for same SHA, got %d", len(commits))
	}
}

func TestResolveSubmodulesNoSubmodules(t *testing.T) {
	sourceRepo := setupTestRepo(t)
	cacheDir := t.TempDir()
	m := NewManager(cacheDir)
	ctx := context.Background()

	path, err := m.EnsureCloned(ctx, sourceRepo)
	if err != nil {
		t.Fatalf("EnsureCloned: %v", err)
	}

	sha := getCommitSHA(t, sourceRepo, "HEAD")

	refs, err := m.ResolveSubmodules(ctx, path, sha)
	if err != nil {
		t.Fatalf("ResolveSubmodules: %v", err)
	}
	if len(refs) != 0 {
		t.Errorf("expected 0 submodule refs, got %d", len(refs))
	}
}

func TestResolveSubmodulesWithSubmodule(t *testing.T) {
	// Create a "library" repo to use as a submodule
	libDir := t.TempDir()
	libRepo := filepath.Join(libDir, "lib-repo")
	if err := os.MkdirAll(libRepo, 0o755); err != nil {
		t.Fatal(err)
	}
	runInRepo := func(repo string, args ...string) {
		t.Helper()
		cmd := exec.Command("git", append([]string{"-C", repo}, args...)...)
		cmd.Env = append(os.Environ(),
			"GIT_AUTHOR_NAME=Test",
			"GIT_AUTHOR_EMAIL=test@example.com",
			"GIT_COMMITTER_NAME=Test",
			"GIT_COMMITTER_EMAIL=test@example.com",
		)
		out, err := cmd.CombinedOutput()
		if err != nil {
			t.Fatalf("git %v in %s failed: %v\n%s", args, repo, err, out)
		}
	}

	runInRepo(libRepo, "init")
	runInRepo(libRepo, "checkout", "-b", "main")
	if err := os.WriteFile(filepath.Join(libRepo, "lib.txt"), []byte("lib"), 0o644); err != nil {
		t.Fatal(err)
	}
	runInRepo(libRepo, "add", "lib.txt")
	runInRepo(libRepo, "commit", "-m", "Library initial commit")

	// Create main repo with the library as a submodule
	mainDir := t.TempDir()
	mainRepo := filepath.Join(mainDir, "main-repo")
	if err := os.MkdirAll(mainRepo, 0o755); err != nil {
		t.Fatal(err)
	}
	runInRepo(mainRepo, "init")
	runInRepo(mainRepo, "checkout", "-b", "main")
	if err := os.WriteFile(filepath.Join(mainRepo, "main.txt"), []byte("main"), 0o644); err != nil {
		t.Fatal(err)
	}
	runInRepo(mainRepo, "add", "main.txt")
	runInRepo(mainRepo, "commit", "-m", "Main initial commit")

	// Add submodule (allow file:// transport for test)
	runInRepo(mainRepo, "-c", "protocol.file.allow=always", "submodule", "add", libRepo, "lib")
	runInRepo(mainRepo, "commit", "-m", "Add library submodule")

	// Now clone and resolve
	cacheDir := t.TempDir()
	m := NewManager(cacheDir)
	ctx := context.Background()

	path, err := m.EnsureCloned(ctx, mainRepo)
	if err != nil {
		t.Fatalf("EnsureCloned: %v", err)
	}

	sha := getCommitSHA(t, mainRepo, "HEAD")
	libSHA := getCommitSHA(t, libRepo, "HEAD")

	refs, err := m.ResolveSubmodules(ctx, path, sha)
	if err != nil {
		t.Fatalf("ResolveSubmodules: %v", err)
	}

	if len(refs) != 1 {
		t.Fatalf("expected 1 submodule ref, got %d", len(refs))
	}

	if refs[0].Path != "lib" {
		t.Errorf("submodule path = %q, want %q", refs[0].Path, "lib")
	}
	if refs[0].Sha != libSHA {
		t.Errorf("submodule SHA = %q, want %q", refs[0].Sha, libSHA)
	}
	if refs[0].URL != libRepo {
		t.Errorf("submodule URL = %q, want %q", refs[0].URL, libRepo)
	}
}

func TestParseGitmodules(t *testing.T) {
	input := `[submodule "SpongeAPI"]
	path = SpongeAPI
	url = https://github.com/SpongePowered/SpongeAPI.git
[submodule "SpongeCommon"]
	path = SpongeCommon
	url = https://github.com/SpongePowered/SpongeCommon.git
`
	result := parseGitmodules([]byte(input))

	if len(result) != 2 {
		t.Fatalf("expected 2 entries, got %d", len(result))
	}
	if result["SpongeAPI"] != "https://github.com/SpongePowered/SpongeAPI.git" {
		t.Errorf("SpongeAPI URL = %q", result["SpongeAPI"])
	}
	if result["SpongeCommon"] != "https://github.com/SpongePowered/SpongeCommon.git" {
		t.Errorf("SpongeCommon URL = %q", result["SpongeCommon"])
	}
}
