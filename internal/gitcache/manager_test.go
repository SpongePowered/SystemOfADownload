package gitcache

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

// ---------------------------------------------------------------------------
// Shared test helpers — used by both Manager and GoGitReader contract tests
// ---------------------------------------------------------------------------

// testGit runs a git command in the given repo directory.
func testGit(t *testing.T, repo string, args ...string) {
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

// testGitWithEnv runs a git command with additional env vars.
func testGitWithEnv(t *testing.T, repo string, env []string, args ...string) {
	t.Helper()
	cmd := exec.Command("git", append([]string{"-C", repo}, args...)...)
	cmd.Env = append(os.Environ(), env...)
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("git %v failed: %v\n%s", args, err, out)
	}
}

// initRepo creates a new git repo in a temp dir and returns its path.
func initRepo(t *testing.T) string {
	t.Helper()
	dir := t.TempDir()
	repo := filepath.Join(dir, "repo")
	if err := os.MkdirAll(repo, 0o755); err != nil {
		t.Fatal(err)
	}
	testGit(t, repo, "init")
	testGit(t, repo, "checkout", "-b", "main")
	return repo
}

// commitFile creates or overwrites a file and commits it, returning the SHA.
func commitFile(t *testing.T, repo, filename, content, message string) string {
	t.Helper()
	if err := os.WriteFile(filepath.Join(repo, filename), []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
	testGit(t, repo, "add", filename)
	testGit(t, repo, "commit", "-m", message)
	return revParse(t, repo, "HEAD")
}

// commitWithBody creates a commit with a multi-line message (subject + body).
func commitWithBody(t *testing.T, repo, filename, content, fullMessage string) string { //nolint:unparam // filename varies by caller intent
	t.Helper()
	if err := os.WriteFile(filepath.Join(repo, filename), []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
	testGit(t, repo, "add", filename)
	testGit(t, repo, "commit", "-m", fullMessage)
	return revParse(t, repo, "HEAD")
}

// commitWithAuthor creates a commit with a specific author.
func commitWithAuthor(t *testing.T, repo, filename, content, message, authorName, authorEmail string) string {
	t.Helper()
	if err := os.WriteFile(filepath.Join(repo, filename), []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
	testGit(t, repo, "add", filename)
	env := []string{
		fmt.Sprintf("GIT_AUTHOR_NAME=%s", authorName),
		fmt.Sprintf("GIT_AUTHOR_EMAIL=%s", authorEmail),
		"GIT_COMMITTER_NAME=Test",
		"GIT_COMMITTER_EMAIL=test@example.com",
	}
	testGitWithEnv(t, repo, env, "commit", "-m", message)
	return revParse(t, repo, "HEAD")
}

// commitWithDate creates a commit with a specific author date.
func commitWithDate(t *testing.T, repo, filename, content, message string, date time.Time) string {
	t.Helper()
	if err := os.WriteFile(filepath.Join(repo, filename), []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
	testGit(t, repo, "add", filename)
	env := []string{
		"GIT_AUTHOR_NAME=Test",
		"GIT_AUTHOR_EMAIL=test@example.com",
		"GIT_COMMITTER_NAME=Test",
		"GIT_COMMITTER_EMAIL=test@example.com",
		fmt.Sprintf("GIT_AUTHOR_DATE=%s", date.Format(time.RFC3339)),
		fmt.Sprintf("GIT_COMMITTER_DATE=%s", date.Format(time.RFC3339)),
	}
	testGitWithEnv(t, repo, env, "commit", "-m", message)
	return revParse(t, repo, "HEAD")
}

// revParse returns the SHA for a ref.
func revParse(t *testing.T, repo, ref string) string { //nolint:unparam // ref varies by caller intent
	t.Helper()
	cmd := exec.Command("git", "-C", repo, "rev-parse", ref)
	out, err := cmd.Output()
	if err != nil {
		t.Fatalf("rev-parse %s: %v", ref, err)
	}
	return strings.TrimSpace(string(out))
}

// cloneForReader clones a source repo via the reader's EnsureCloned and returns the handle.
func cloneForReader(t *testing.T, reader RepoReader, sourceRepo string) string {
	t.Helper()
	handle, err := reader.EnsureCloned(context.Background(), sourceRepo)
	if err != nil {
		t.Fatalf("EnsureCloned: %v", err)
	}
	return handle
}

// readerFactory is a function that creates a RepoReader for a given cache dir.
type readerFactory func(cacheDir string) RepoReader

// ---------------------------------------------------------------------------
// Setup helpers for specific repo topologies
// ---------------------------------------------------------------------------

// setupBasicRepo creates a repo with 3 linear commits.
func setupBasicRepo(t *testing.T) (repo string, shas [3]string) {
	t.Helper()
	repo = initRepo(t)
	shas[0] = commitFile(t, repo, "file.txt", "hello", "Initial commit")
	shas[1] = commitFile(t, repo, "file.txt", "world", "Second commit")
	shas[2] = commitFile(t, repo, "file.txt", "!", "Third commit")
	return
}

// setupMergeRepo creates a repo with a no-ff merge.
//
//	main: A -- B -- M (merge)
//	               /
//	feature:  C --
func setupMergeRepo(t *testing.T) (repo, baseSHA, featureSHA, mergeSHA string) {
	t.Helper()
	repo = initRepo(t)
	baseSHA = commitFile(t, repo, "file.txt", "base", "Base commit")
	testGit(t, repo, "checkout", "-b", "feature")
	featureSHA = commitFile(t, repo, "feature.txt", "feature work", "Feature commit")
	testGit(t, repo, "checkout", "main")
	_ = commitFile(t, repo, "file.txt", "main work", "Main commit")
	testGit(t, repo, "merge", "--no-ff", "feature", "-m", "Merge feature branch")
	mergeSHA = revParse(t, repo, "HEAD")
	return
}

// diamondSHAs holds the commit SHAs from a diamond merge repo.
type diamondSHAs struct {
	A, B, C, D, E string
}

// setupDiamondMergeRepo creates a diamond merge:
//
//	A -- B -- D -- E (merge of C and D)
//	      \      /
//	       C ---
func setupDiamondMergeRepo(t *testing.T) (string, diamondSHAs) {
	t.Helper()
	repo := initRepo(t)
	var s diamondSHAs
	s.A = commitFile(t, repo, "file.txt", "a", "Commit A")
	s.B = commitFile(t, repo, "file.txt", "b", "Commit B")
	testGit(t, repo, "checkout", "-b", "side")
	s.C = commitFile(t, repo, "side.txt", "c", "Commit C")
	testGit(t, repo, "checkout", "main")
	s.D = commitFile(t, repo, "file.txt", "d", "Commit D")
	testGit(t, repo, "merge", "--no-ff", "side", "-m", "Commit E (merge)")
	s.E = revParse(t, repo, "HEAD")
	return repo, s
}

// setupSubmoduleRepo creates a repo with submodules.
func setupSubmoduleRepo(t *testing.T, numSubmodules int) (mainRepo string, subRepos []string, mainSHA string, subSHAs []string) {
	t.Helper()
	mainRepo = initRepo(t)
	commitFile(t, mainRepo, "main.txt", "main", "Main initial")

	for i := range numSubmodules {
		subRepo := initRepo(t)
		subName := fmt.Sprintf("sub%d", i)
		sha := commitFile(t, subRepo, "lib.txt", fmt.Sprintf("lib%d", i), fmt.Sprintf("Sub%d initial", i))
		subRepos = append(subRepos, subRepo)
		subSHAs = append(subSHAs, sha)
		testGit(t, mainRepo, "-c", "protocol.file.allow=always", "submodule", "add", subRepo, subName)
	}
	testGit(t, mainRepo, "commit", "-m", "Add submodules")
	mainSHA = revParse(t, mainRepo, "HEAD")
	return
}

// ---------------------------------------------------------------------------
// Contract test suite — parameterized on RepoReader implementation
// ---------------------------------------------------------------------------

func testRepoReaderContract(t *testing.T, factory readerFactory) {
	t.Run("GetCommitDetails/basic", func(t *testing.T) {
		repo, shas := setupBasicRepo(t)
		reader := factory(t.TempDir())
		handle := cloneForReader(t, reader, repo)

		details, err := reader.GetCommitDetails(context.Background(), handle, shas[2])
		if err != nil {
			t.Fatalf("GetCommitDetails: %v", err)
		}
		if details.Sha != shas[2] {
			t.Errorf("SHA = %s, want %s", details.Sha, shas[2])
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
	})

	t.Run("ComputeChangelog/basic", func(t *testing.T) {
		repo, shas := setupBasicRepo(t)
		reader := factory(t.TempDir())
		handle := cloneForReader(t, reader, repo)

		commits, truncated, err := reader.ComputeChangelog(context.Background(), handle, shas[0], shas[2])
		if err != nil {
			t.Fatalf("ComputeChangelog: %v", err)
		}
		if truncated {
			t.Error("expected truncated=false")
		}
		if len(commits) != 2 {
			t.Fatalf("expected 2 commits, got %d", len(commits))
		}
		if commits[0].Message != "Third commit" {
			t.Errorf("commits[0].Message = %q, want %q", commits[0].Message, "Third commit")
		}
		if commits[1].Message != "Second commit" {
			t.Errorf("commits[1].Message = %q, want %q", commits[1].Message, "Second commit")
		}
	})

	t.Run("ComputeChangelog/sameSHA", func(t *testing.T) {
		repo, shas := setupBasicRepo(t)
		reader := factory(t.TempDir())
		handle := cloneForReader(t, reader, repo)

		commits, _, err := reader.ComputeChangelog(context.Background(), handle, shas[2], shas[2])
		if err != nil {
			t.Fatalf("ComputeChangelog: %v", err)
		}
		if len(commits) != 0 {
			t.Errorf("expected 0 commits for same SHA, got %d", len(commits))
		}
	})

	t.Run("ResolveSubmodules/noSubmodules", func(t *testing.T) {
		repo, shas := setupBasicRepo(t)
		reader := factory(t.TempDir())
		handle := cloneForReader(t, reader, repo)

		refs, err := reader.ResolveSubmodules(context.Background(), handle, shas[2])
		if err != nil {
			t.Fatalf("ResolveSubmodules: %v", err)
		}
		if len(refs) != 0 {
			t.Errorf("expected 0 refs, got %d", len(refs))
		}
	})

	t.Run("ResolveSubmodules/withSubmodule", func(t *testing.T) {
		mainRepo, subRepos, mainSHA, subSHAs := setupSubmoduleRepo(t, 1)
		reader := factory(t.TempDir())
		handle := cloneForReader(t, reader, mainRepo)

		refs, err := reader.ResolveSubmodules(context.Background(), handle, mainSHA)
		if err != nil {
			t.Fatalf("ResolveSubmodules: %v", err)
		}
		if len(refs) != 1 {
			t.Fatalf("expected 1 ref, got %d", len(refs))
		}
		if refs[0].Path != "sub0" {
			t.Errorf("path = %q, want %q", refs[0].Path, "sub0")
		}
		if refs[0].Sha != subSHAs[0] {
			t.Errorf("sha = %q, want %q", refs[0].Sha, subSHAs[0])
		}
		if refs[0].URL != subRepos[0] {
			t.Errorf("url = %q, want %q", refs[0].URL, subRepos[0])
		}
	})
}

// ---------------------------------------------------------------------------
// Manager-specific tests
// ---------------------------------------------------------------------------

func TestShellManagerContract(t *testing.T) {
	testRepoReaderContract(t, func(cacheDir string) RepoReader {
		return NewManager(cacheDir)
	})
}

func TestRepoPath(t *testing.T) {
	m := NewManager("/tmp/test-cache")

	tests := []struct {
		url        string
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
		if want := "/tmp/test-cache/" + tt.wantSuffix; got != want {
			t.Errorf("repoPath(%q) = %s, want %s", tt.url, got, want)
		}
	}
}

func TestEnsureClonedAndFetch(t *testing.T) {
	repo, _ := setupBasicRepo(t)
	cacheDir := t.TempDir()
	m := NewManager(cacheDir)
	ctx := context.Background()

	path, err := m.EnsureCloned(ctx, repo)
	if err != nil {
		t.Fatalf("EnsureCloned (clone): %v", err)
	}
	if _, err := os.Stat(filepath.Join(path, "HEAD")); err != nil {
		t.Fatalf("expected HEAD in bare repo: %v", err)
	}

	path2, err := m.EnsureCloned(ctx, repo)
	if err != nil {
		t.Fatalf("EnsureCloned (fetch): %v", err)
	}
	if path != path2 {
		t.Errorf("paths differ: %s vs %s", path, path2)
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
