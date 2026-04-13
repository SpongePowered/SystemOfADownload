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

	"github.com/google/go-cmp/cmp"
)

// ---------------------------------------------------------------------------
// GoGitReader contract tests (same suite as Manager)
// ---------------------------------------------------------------------------

func TestGoGitReaderContract(t *testing.T) {
	testRepoReaderContract(t, func(cacheDir string) RepoReader {
		return NewGoGitReader(NewManager(cacheDir))
	})
}

// ---------------------------------------------------------------------------
// Dual-backend comparison harness
// ---------------------------------------------------------------------------

func TestDualBackendComparison(t *testing.T) {
	repo, shas := setupBasicRepo(t)

	shellDir := t.TempDir()
	gogitDir := t.TempDir()
	shell := NewManager(shellDir)
	gogit := NewGoGitReader(NewManager(gogitDir))
	ctx := context.Background()

	shellHandle := cloneForReader(t, shell, repo)
	gogitHandle := cloneForReader(t, gogit, repo)

	t.Run("GetCommitDetails", func(t *testing.T) {
		for i, sha := range shas {
			t.Run(fmt.Sprintf("commit_%d", i), func(t *testing.T) {
				want, err := shell.GetCommitDetails(ctx, shellHandle, sha)
				if err != nil {
					t.Fatalf("shell.GetCommitDetails: %v", err)
				}
				got, err := gogit.GetCommitDetails(ctx, gogitHandle, sha)
				if err != nil {
					t.Fatalf("gogit.GetCommitDetails: %v", err)
				}
				if diff := cmp.Diff(want, got); diff != "" {
					t.Errorf("GetCommitDetails mismatch (-shell +gogit):\n%s", diff)
				}
			})
		}
	})

	t.Run("ComputeChangelog", func(t *testing.T) {
		wantCommits, wantTrunc, err := shell.ComputeChangelog(ctx, shellHandle, shas[0], shas[2])
		if err != nil {
			t.Fatalf("shell.ComputeChangelog: %v", err)
		}
		gotCommits, gotTrunc, err := gogit.ComputeChangelog(ctx, gogitHandle, shas[0], shas[2])
		if err != nil {
			t.Fatalf("gogit.ComputeChangelog: %v", err)
		}
		if wantTrunc != gotTrunc {
			t.Errorf("truncated: shell=%v, gogit=%v", wantTrunc, gotTrunc)
		}
		if diff := cmp.Diff(wantCommits, gotCommits); diff != "" {
			t.Errorf("ComputeChangelog mismatch (-shell +gogit):\n%s", diff)
		}
	})

	t.Run("ResolveSubmodules", func(t *testing.T) {
		for _, sha := range shas {
			wantRefs, err := shell.ResolveSubmodules(ctx, shellHandle, sha)
			if err != nil {
				t.Fatalf("shell.ResolveSubmodules: %v", err)
			}
			gotRefs, err := gogit.ResolveSubmodules(ctx, gogitHandle, sha)
			if err != nil {
				t.Fatalf("gogit.ResolveSubmodules: %v", err)
			}
			if diff := cmp.Diff(wantRefs, gotRefs); diff != "" {
				t.Errorf("ResolveSubmodules(%s) mismatch (-shell +gogit):\n%s", sha[:8], diff)
			}
		}
	})
}

// ---------------------------------------------------------------------------
// Edge case matrix (section 13 of FINAL-PLAN.md)
// ---------------------------------------------------------------------------

// Row 1: SHA not found in repo
func TestEdge_SHANotFound(t *testing.T) {
	repo, _ := setupBasicRepo(t)
	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			_, err := reader.GetCommitDetails(context.Background(), handle, "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef")
			if err == nil {
				t.Error("expected error for nonexistent SHA")
			}
		})
	}
}

// Row 2: Empty commit body
func TestEdge_EmptyBody(t *testing.T) {
	repo := initRepo(t)
	sha := commitFile(t, repo, "f.txt", "x", "Subject only")

	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			d, err := reader.GetCommitDetails(context.Background(), handle, sha)
			if err != nil {
				t.Fatal(err)
			}
			if d.Message != "Subject only" {
				t.Errorf("Message = %q", d.Message)
			}
			if d.Body != "" {
				t.Errorf("Body = %q, want empty", d.Body)
			}
		})
	}
}

// Row 3: Multi-line body with blank lines
func TestEdge_MultiLineBody(t *testing.T) {
	repo := initRepo(t)
	msg := "Subject line\n\nParagraph 1\n\nParagraph 2"
	sha := commitWithBody(t, repo, "f.txt", "x", msg)

	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			d, err := reader.GetCommitDetails(context.Background(), handle, sha)
			if err != nil {
				t.Fatal(err)
			}
			if d.Message != "Subject line" {
				t.Errorf("Message = %q, want %q", d.Message, "Subject line")
			}
			if !strings.Contains(d.Body, "Paragraph 1") || !strings.Contains(d.Body, "Paragraph 2") {
				t.Errorf("Body = %q, expected both paragraphs", d.Body)
			}
		})
	}
}

// Row 4: Unicode in author name
func TestEdge_UnicodeAuthor(t *testing.T) {
	repo := initRepo(t)
	sha := commitWithAuthor(t, repo, "f.txt", "x", "Unicode author",
		"Rene\u0301 Muster\u00e4ng", "rene@example.com")

	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			d, err := reader.GetCommitDetails(context.Background(), handle, sha)
			if err != nil {
				t.Fatal(err)
			}
			if d.AuthorName != "Rene\u0301 Muster\u00e4ng" {
				t.Errorf("AuthorName = %q", d.AuthorName)
			}
		})
	}
}

// Row 5: Unicode in commit message
func TestEdge_UnicodeMessage(t *testing.T) {
	repo := initRepo(t)
	sha := commitFile(t, repo, "f.txt", "x", "\U0001F680 Launch \u4e16\u754c")

	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			d, err := reader.GetCommitDetails(context.Background(), handle, sha)
			if err != nil {
				t.Fatal(err)
			}
			if d.Message != "\U0001F680 Launch \u4e16\u754c" {
				t.Errorf("Message = %q", d.Message)
			}
		})
	}
}

// Row 6: Merge commit (2 parents)
func TestEdge_MergeCommit(t *testing.T) {
	repo, _, _, mergeSHA := setupMergeRepo(t)

	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			d, err := reader.GetCommitDetails(context.Background(), handle, mergeSHA)
			if err != nil {
				t.Fatal(err)
			}
			if d.Sha != mergeSHA {
				t.Errorf("SHA = %s, want %s", d.Sha, mergeSHA)
			}
			if !strings.Contains(d.Message, "Merge") {
				t.Errorf("expected merge message, got %q", d.Message)
			}
		})
	}
}

// Row 7: Octopus merge (3+ parents)
func TestEdge_OctopusMerge(t *testing.T) {
	repo := initRepo(t)
	commitFile(t, repo, "f.txt", "base", "Base")
	testGit(t, repo, "checkout", "-b", "branchA")
	commitFile(t, repo, "a.txt", "a", "Branch A commit")
	testGit(t, repo, "checkout", "main")
	testGit(t, repo, "checkout", "-b", "branchB")
	commitFile(t, repo, "b.txt", "b", "Branch B commit")
	testGit(t, repo, "checkout", "main")
	testGit(t, repo, "merge", "--no-ff", "branchA", "branchB", "-m", "Octopus merge")
	octopusSHA := revParse(t, repo, "HEAD")

	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			d, err := reader.GetCommitDetails(context.Background(), handle, octopusSHA)
			if err != nil {
				t.Fatal(err)
			}
			if d.Sha != octopusSHA {
				t.Errorf("SHA = %s, want %s", d.Sha, octopusSHA)
			}
		})
	}
}

// Row 9: No .gitmodules
func TestEdge_NoGitmodules(t *testing.T) {
	repo, shas := setupBasicRepo(t)
	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			refs, err := reader.ResolveSubmodules(context.Background(), handle, shas[0])
			if err != nil {
				t.Fatalf("expected nil error, got: %v", err)
			}
			if refs != nil {
				t.Errorf("expected nil refs, got %v", refs)
			}
		})
	}
}

// Row 10: .gitmodules with relative URL
func TestEdge_RelativeURLSubmodule(t *testing.T) {
	// Create a submodule with a relative URL in .gitmodules
	repo := initRepo(t)
	subRepo := initRepo(t)
	commitFile(t, subRepo, "lib.txt", "lib", "Sub initial")

	commitFile(t, repo, "main.txt", "main", "Main initial")
	testGit(t, repo, "-c", "protocol.file.allow=always", "submodule", "add", subRepo, "lib")
	testGit(t, repo, "commit", "-m", "Add submodule")

	// Rewrite the URL to relative in .gitmodules
	gitmodulesPath := filepath.Join(repo, ".gitmodules")
	content, _ := os.ReadFile(gitmodulesPath)
	newContent := strings.Replace(string(content), subRepo, "../other-repo.git", 1)
	if err := os.WriteFile(gitmodulesPath, []byte(newContent), 0o644); err != nil { //nolint:gosec // test temp dir
		t.Fatal(err)
	}
	testGit(t, repo, "add", ".gitmodules")
	testGit(t, repo, "commit", "-m", "Use relative URL")
	sha := revParse(t, repo, "HEAD")

	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			refs, err := reader.ResolveSubmodules(context.Background(), handle, sha)
			if err != nil {
				t.Fatalf("ResolveSubmodules: %v", err)
			}
			if len(refs) != 1 {
				t.Fatalf("expected 1 ref, got %d", len(refs))
			}
			if refs[0].URL != "../other-repo.git" {
				t.Errorf("URL = %q, want %q", refs[0].URL, "../other-repo.git")
			}
		})
	}
}

// Row 11: .gitmodules with SSH URL
func TestEdge_SSHURLSubmodule(t *testing.T) {
	repo := initRepo(t)
	subRepo := initRepo(t)
	commitFile(t, subRepo, "lib.txt", "lib", "Sub initial")

	commitFile(t, repo, "main.txt", "main", "Main initial")
	testGit(t, repo, "-c", "protocol.file.allow=always", "submodule", "add", subRepo, "lib")
	testGit(t, repo, "commit", "-m", "Add submodule")

	// Rewrite URL to SSH
	gitmodulesPath := filepath.Join(repo, ".gitmodules")
	content, _ := os.ReadFile(gitmodulesPath)
	newContent := strings.Replace(string(content), subRepo, "git@github.com:Org/Repo.git", 1)
	if err := os.WriteFile(gitmodulesPath, []byte(newContent), 0o644); err != nil { //nolint:gosec // test temp dir
		t.Fatal(err)
	}
	testGit(t, repo, "add", ".gitmodules")
	testGit(t, repo, "commit", "-m", "Use SSH URL")
	sha := revParse(t, repo, "HEAD")

	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			refs, err := reader.ResolveSubmodules(context.Background(), handle, sha)
			if err != nil {
				t.Fatalf("ResolveSubmodules: %v", err)
			}
			if len(refs) != 1 {
				t.Fatalf("expected 1 ref, got %d", len(refs))
			}
			if refs[0].URL != "git@github.com:Org/Repo.git" {
				t.Errorf("URL = %q", refs[0].URL)
			}
		})
	}
}

// Row 13: Multiple submodules
func TestEdge_MultipleSubmodules(t *testing.T) {
	mainRepo, _, mainSHA, _ := setupSubmoduleRepo(t, 3)

	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, mainRepo)
			refs, err := reader.ResolveSubmodules(context.Background(), handle, mainSHA)
			if err != nil {
				t.Fatalf("ResolveSubmodules: %v", err)
			}
			if len(refs) != 3 {
				t.Fatalf("expected 3 refs, got %d", len(refs))
			}
		})
	}
}

// Row 15: Same SHA both sides
func TestEdge_ChangelogSameSHA(t *testing.T) {
	repo, shas := setupBasicRepo(t)
	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			commits, truncated, err := reader.ComputeChangelog(context.Background(), handle, shas[0], shas[0])
			if err != nil {
				t.Fatal(err)
			}
			if truncated {
				t.Error("expected truncated=false")
			}
			if len(commits) != 0 {
				t.Errorf("expected 0 commits, got %d", len(commits))
			}
		})
	}
}

// Row 16: Single commit apart
func TestEdge_ChangelogSingleCommit(t *testing.T) {
	repo, shas := setupBasicRepo(t)
	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			commits, truncated, err := reader.ComputeChangelog(context.Background(), handle, shas[1], shas[2])
			if err != nil {
				t.Fatal(err)
			}
			if truncated {
				t.Error("expected truncated=false")
			}
			if len(commits) != 1 {
				t.Fatalf("expected 1 commit, got %d", len(commits))
			}
			if commits[0].Message != "Third commit" {
				t.Errorf("Message = %q", commits[0].Message)
			}
		})
	}
}

// Row 17+18: Truncation at 500/501 commits
func TestEdge_ChangelogTruncation(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping slow truncation test")
	}

	repo := initRepo(t)
	firstSHA := commitFile(t, repo, "f.txt", "start", "Initial")

	// Create 501 additional commits
	for i := range 501 {
		commitFile(t, repo, "f.txt", fmt.Sprintf("v%d", i), fmt.Sprintf("Commit %d", i))
	}
	headSHA := revParse(t, repo, "HEAD")

	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			commits, truncated, err := reader.ComputeChangelog(context.Background(), handle, firstSHA, headSHA)
			if err != nil {
				t.Fatal(err)
			}
			if !truncated {
				t.Error("expected truncated=true for 501 commits")
			}
			if len(commits) != MaxChangelogCommits {
				t.Errorf("expected %d commits, got %d", MaxChangelogCommits, len(commits))
			}
		})
	}
}

// Row 24: CommitDate timezone (non-UTC)
func TestEdge_CommitDateTimezone(t *testing.T) {
	repo := initRepo(t)
	loc := time.FixedZone("IST", 5*3600+30*60) // +05:30
	date := time.Date(2025, 6, 15, 14, 30, 0, 0, loc)
	sha := commitWithDate(t, repo, "f.txt", "tz", "Timezone commit", date)

	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			d, err := reader.GetCommitDetails(context.Background(), handle, sha)
			if err != nil {
				t.Fatal(err)
			}
			if !strings.Contains(d.CommitDate, "+05:30") {
				t.Errorf("CommitDate = %q, expected +05:30 offset", d.CommitDate)
			}
		})
	}
}

// Row 25: Very long commit message (>64KB)
func TestEdge_LongMessage(t *testing.T) {
	repo := initRepo(t)
	longBody := strings.Repeat("A", 70000)
	msg := "Long subject\n\n" + longBody
	sha := commitWithBody(t, repo, "f.txt", "x", msg)

	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			d, err := reader.GetCommitDetails(context.Background(), handle, sha)
			if err != nil {
				t.Fatal(err)
			}
			if d.Message != "Long subject" {
				t.Errorf("Message = %q", d.Message)
			}
			if len(d.Body) < 60000 {
				t.Errorf("Body length = %d, expected >60000", len(d.Body))
			}
		})
	}
}

// Row 27: Feature branch merged via --no-ff in changelog
func TestEdge_ChangelogNoFFMerge(t *testing.T) {
	repo, baseSHA, featureSHA, mergeSHA := setupMergeRepo(t)

	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			commits, _, err := reader.ComputeChangelog(context.Background(), handle, baseSHA, mergeSHA)
			if err != nil {
				t.Fatal(err)
			}
			// Must include the feature commit
			found := false
			for _, c := range commits {
				if c.Sha == featureSHA {
					found = true
					break
				}
			}
			if !found {
				t.Errorf("feature commit %s not found in changelog (got %d commits)", featureSHA[:8], len(commits))
				for _, c := range commits {
					t.Logf("  %s %s", c.Sha[:8], c.Message)
				}
			}
		})
	}
}

// Row 29: Diamond merge changelog
func TestEdge_ChangelogDiamondMerge(t *testing.T) {
	repo, d := setupDiamondMergeRepo(t)

	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			commits, _, err := reader.ComputeChangelog(context.Background(), handle, d.B, d.E)
			if err != nil {
				t.Fatal(err)
			}

			shaSet := make(map[string]bool)
			for _, c := range commits {
				shaSet[c.Sha] = true
			}
			// Changelog(B, E) must include E, D, and C
			for _, expected := range []struct {
				sha  string
				name string
			}{
				{d.E, "E (merge)"},
				{d.D, "D"},
				{d.C, "C"},
			} {
				if !shaSet[expected.sha] {
					t.Errorf("expected commit %s (%s) in changelog", expected.sha[:8], expected.name)
				}
			}
			// Must NOT include B (it's the fromSHA)
			if shaSet[d.B] {
				t.Error("fromSHA (B) should not be in changelog")
			}
		})
	}
}

// Row 30: Merge commit as fromSHA
func TestEdge_ChangelogMergeAsFrom(t *testing.T) {
	repo, _, _, mergeSHA := setupMergeRepo(t)

	// Add a commit after the merge
	afterSHA := commitFile(t, repo, "after.txt", "after", "After merge")

	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			commits, _, err := reader.ComputeChangelog(context.Background(), handle, mergeSHA, afterSHA)
			if err != nil {
				t.Fatal(err)
			}
			if len(commits) != 1 {
				t.Fatalf("expected 1 commit, got %d", len(commits))
			}
			if commits[0].Sha != afterSHA {
				t.Errorf("expected commit %s, got %s", afterSHA[:8], commits[0].Sha[:8])
			}
		})
	}
}

// Row 31: Merge commit as toSHA
func TestEdge_ChangelogMergeAsTo(t *testing.T) {
	repo, baseSHA, featureSHA, mergeSHA := setupMergeRepo(t)

	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			commits, _, err := reader.ComputeChangelog(context.Background(), handle, baseSHA, mergeSHA)
			if err != nil {
				t.Fatal(err)
			}
			// Must include all commits reachable from merge but not from base
			shaSet := make(map[string]bool)
			for _, c := range commits {
				shaSet[c.Sha] = true
			}
			if !shaSet[featureSHA] {
				t.Error("feature commit should be in changelog when merge is toSHA")
			}
			if !shaSet[mergeSHA] {
				t.Error("merge commit itself should be in changelog")
			}
		})
	}
}

// Row 32: UTC commit date — GoGit must emit +00:00, not Z.
// The shell backend's behavior is system-dependent (git %aI may emit Z on
// some platforms), so only the GoGit backend is asserted here.
func TestEdge_CommitDateUTC(t *testing.T) {
	repo := initRepo(t)
	utcDate := time.Date(2025, 6, 15, 14, 30, 0, 0, time.UTC)
	sha := commitWithDate(t, repo, "f.txt", "utc", "UTC commit", utcDate)

	reader := NewGoGitReader(NewManager(t.TempDir()))
	handle := cloneForReader(t, reader, repo)
	d, err := reader.GetCommitDetails(context.Background(), handle, sha)
	if err != nil {
		t.Fatal(err)
	}
	if strings.HasSuffix(d.CommitDate, "Z") {
		t.Errorf("CommitDate = %q, must use +00:00 not Z", d.CommitDate)
	}
	if !strings.HasSuffix(d.CommitDate, "+00:00") {
		t.Errorf("CommitDate = %q, expected +00:00 suffix", d.CommitDate)
	}
}

// Row 34: Half-hour timezone offset (+05:30)
func TestEdge_CommitDateHalfHour(t *testing.T) {
	repo := initRepo(t)
	loc := time.FixedZone("IST", 5*3600+30*60)
	date := time.Date(2025, 3, 10, 9, 45, 0, 0, loc)
	sha := commitWithDate(t, repo, "f.txt", "hh", "Half-hour tz", date)

	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			d, err := reader.GetCommitDetails(context.Background(), handle, sha)
			if err != nil {
				t.Fatal(err)
			}
			if !strings.Contains(d.CommitDate, "+05:30") {
				t.Errorf("CommitDate = %q, expected +05:30", d.CommitDate)
			}
		})
	}
}

// Row 23: Fetch after new commits
func TestEdge_FetchAfterNewCommits(t *testing.T) {
	// Each factory gets its own source repo to avoid shared mutation.
	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			repo, shas := setupBasicRepo(t)
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)

			// Verify initial clone works
			_, err := reader.GetCommitDetails(context.Background(), handle, shas[2])
			if err != nil {
				t.Fatalf("GetCommitDetails before fetch: %v", err)
			}

			// Add a new commit to the source repo
			newSHA := commitFile(t, repo, "new.txt", "new", "New commit after clone")

			// Re-fetch
			handle2, err := reader.EnsureCloned(context.Background(), repo)
			if err != nil {
				t.Fatalf("EnsureCloned (fetch): %v", err)
			}
			if handle != handle2 {
				t.Errorf("handles differ: %s vs %s", handle, handle2)
			}

			// Verify new commit is visible
			d, err := reader.GetCommitDetails(context.Background(), handle2, newSHA)
			if err != nil {
				t.Fatalf("GetCommitDetails after fetch: %v", err)
			}
			if d.Message != "New commit after clone" {
				t.Errorf("Message = %q", d.Message)
			}
		})
	}
}

// Row 28: Multiple merges in range
func TestEdge_ChangelogMultipleMerges(t *testing.T) {
	repo := initRepo(t)
	baseSHA := commitFile(t, repo, "f.txt", "base", "Base")

	// First feature branch + merge
	testGit(t, repo, "checkout", "-b", "feat1")
	feat1SHA := commitFile(t, repo, "feat1.txt", "f1", "Feature 1")
	testGit(t, repo, "checkout", "main")
	testGit(t, repo, "merge", "--no-ff", "feat1", "-m", "Merge feat1")

	// Second feature branch + merge
	testGit(t, repo, "checkout", "-b", "feat2")
	feat2SHA := commitFile(t, repo, "feat2.txt", "f2", "Feature 2")
	testGit(t, repo, "checkout", "main")
	testGit(t, repo, "merge", "--no-ff", "feat2", "-m", "Merge feat2")
	headSHA := revParse(t, repo, "HEAD")

	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			commits, _, err := reader.ComputeChangelog(context.Background(), handle, baseSHA, headSHA)
			if err != nil {
				t.Fatal(err)
			}
			shaSet := make(map[string]bool)
			for _, c := range commits {
				shaSet[c.Sha] = true
			}
			if !shaSet[feat1SHA] {
				t.Error("feature 1 commit missing from changelog")
			}
			if !shaSet[feat2SHA] {
				t.Error("feature 2 commit missing from changelog")
			}
		})
	}
}

// ---------------------------------------------------------------------------
// splitMessage unit tests (section 12.7: body-field fidelity)
// ---------------------------------------------------------------------------

func TestSplitMessage(t *testing.T) {
	tests := []struct {
		name        string
		input       string
		wantSubject string
		wantBody    string
	}{
		{"empty", "", "", ""},
		{"subject_only", "Fix the build", "Fix the build", ""},
		{"subject_and_body", "Fix the build\n\nDetailed explanation.", "Fix the build", "Detailed explanation."},
		{"subject_body_multi_paragraph", "Fix\n\nParagraph 1\n\nParagraph 2", "Fix", "Paragraph 1\n\nParagraph 2"},
		{"trailing_newlines", "Subject\n\nBody\n\n\n", "Subject", "Body"},
		{"whitespace_only", "   \n  \n  ", "", ""},
		{"subject_with_continuation", "Line 1\nLine 2\n\nBody", "Line 1", "Line 2\n\nBody"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			gotSubject, gotBody := splitMessage(tt.input)
			if gotSubject != tt.wantSubject {
				t.Errorf("subject = %q, want %q", gotSubject, tt.wantSubject)
			}
			if gotBody != tt.wantBody {
				t.Errorf("body = %q, want %q", gotBody, tt.wantBody)
			}
		})
	}
}

// ---------------------------------------------------------------------------
// ComputeChangelog body field contract: Body must be empty
// ---------------------------------------------------------------------------

func TestComputeChangelogBodyIsEmpty(t *testing.T) {
	repo := initRepo(t)
	firstSHA := commitWithBody(t, repo, "f.txt", "v1", "First\n\nWith body text")
	secondSHA := commitWithBody(t, repo, "f.txt", "v2", "Second\n\nAnother body")

	for _, factory := range readerFactories(t) {
		t.Run(factory.name, func(t *testing.T) {
			reader := factory.fn(t.TempDir())
			handle := cloneForReader(t, reader, repo)
			commits, _, err := reader.ComputeChangelog(context.Background(), handle, firstSHA, secondSHA)
			if err != nil {
				t.Fatal(err)
			}
			if len(commits) != 1 {
				t.Fatalf("expected 1 commit, got %d", len(commits))
			}
			if commits[0].Body != "" {
				t.Errorf("ComputeChangelog should not populate Body, got %q", commits[0].Body)
			}
		})
	}
}

// ---------------------------------------------------------------------------
// Benchmarks (section 12.9)
// ---------------------------------------------------------------------------

func BenchmarkGetCommitDetails_Shell(b *testing.B) {
	benchGetCommitDetails(b, func(cacheDir string) RepoReader {
		return NewManager(cacheDir)
	})
}

func BenchmarkGetCommitDetails_GoGit(b *testing.B) {
	benchGetCommitDetails(b, func(cacheDir string) RepoReader {
		return NewGoGitReader(NewManager(cacheDir))
	})
}

func BenchmarkComputeChangelog_Shell(b *testing.B) {
	benchComputeChangelog(b, func(cacheDir string) RepoReader {
		return NewManager(cacheDir)
	})
}

func BenchmarkComputeChangelog_GoGit(b *testing.B) {
	benchComputeChangelog(b, func(cacheDir string) RepoReader {
		return NewGoGitReader(NewManager(cacheDir))
	})
}

func BenchmarkResolveSubmodules_Shell(b *testing.B) {
	benchResolveSubmodules(b, func(cacheDir string) RepoReader {
		return NewManager(cacheDir)
	})
}

func BenchmarkResolveSubmodules_GoGit(b *testing.B) {
	benchResolveSubmodules(b, func(cacheDir string) RepoReader {
		return NewGoGitReader(NewManager(cacheDir))
	})
}

func benchGetCommitDetails(b *testing.B, factory readerFactory) {
	b.Helper()
	repo := benchSetupRepo(b)
	cacheDir := b.TempDir()
	reader := factory(cacheDir)
	handle, err := reader.EnsureCloned(context.Background(), repo)
	if err != nil {
		b.Fatal(err)
	}
	sha := benchRevParse(b, repo, "HEAD")
	ctx := context.Background()

	b.ResetTimer()
	for range b.N {
		_, err := reader.GetCommitDetails(ctx, handle, sha)
		if err != nil {
			b.Fatal(err)
		}
	}
}

func benchComputeChangelog(b *testing.B, factory readerFactory) {
	b.Helper()
	repo := benchSetupRepo(b)
	cacheDir := b.TempDir()
	reader := factory(cacheDir)
	handle, err := reader.EnsureCloned(context.Background(), repo)
	if err != nil {
		b.Fatal(err)
	}
	firstSHA := benchRevParse(b, repo, "HEAD~2")
	headSHA := benchRevParse(b, repo, "HEAD")
	ctx := context.Background()

	b.ResetTimer()
	for range b.N {
		_, _, err := reader.ComputeChangelog(ctx, handle, firstSHA, headSHA)
		if err != nil {
			b.Fatal(err)
		}
	}
}

func benchResolveSubmodules(b *testing.B, factory readerFactory) {
	b.Helper()
	repo := benchSetupRepo(b)
	cacheDir := b.TempDir()
	reader := factory(cacheDir)
	handle, err := reader.EnsureCloned(context.Background(), repo)
	if err != nil {
		b.Fatal(err)
	}
	sha := benchRevParse(b, repo, "HEAD")
	ctx := context.Background()

	b.ResetTimer()
	for range b.N {
		_, err := reader.ResolveSubmodules(ctx, handle, sha)
		if err != nil {
			b.Fatal(err)
		}
	}
}

func benchSetupRepo(b *testing.B) string {
	b.Helper()
	dir := b.TempDir()
	repo := filepath.Join(dir, "bench-repo")
	if err := os.MkdirAll(repo, 0o755); err != nil {
		b.Fatal(err)
	}
	benchGit(b, repo, "init")
	benchGit(b, repo, "checkout", "-b", "main")
	for i := range 10 {
		if err := os.WriteFile(filepath.Join(repo, "f.txt"), []byte(fmt.Sprintf("%d", i)), 0o644); err != nil {
			b.Fatal(err)
		}
		benchGit(b, repo, "add", "f.txt")
		benchGit(b, repo, "commit", "-m", fmt.Sprintf("Commit %d", i))
	}
	return repo
}

func benchGit(b *testing.B, repo string, args ...string) {
	b.Helper()
	cmd := exec.Command("git", append([]string{"-C", repo}, args...)...)
	cmd.Env = append(os.Environ(),
		"GIT_AUTHOR_NAME=Bench",
		"GIT_AUTHOR_EMAIL=bench@example.com",
		"GIT_COMMITTER_NAME=Bench",
		"GIT_COMMITTER_EMAIL=bench@example.com",
	)
	if out, err := cmd.CombinedOutput(); err != nil {
		b.Fatalf("git %v: %v\n%s", args, err, out)
	}
}

func benchRevParse(b *testing.B, repo, ref string) string {
	b.Helper()
	cmd := exec.Command("git", "-C", repo, "rev-parse", ref)
	out, err := cmd.Output()
	if err != nil {
		b.Fatalf("rev-parse %s: %v", ref, err)
	}
	return strings.TrimSpace(string(out))
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

type namedFactory struct {
	name string
	fn   readerFactory
}

func readerFactories(_ *testing.T) []namedFactory {
	return []namedFactory{
		{"Shell", func(d string) RepoReader { return NewManager(d) }},
		{"GoGit", func(d string) RepoReader { return NewGoGitReader(NewManager(d)) }},
	}
}
