package gitcache

import (
	"context"
	"fmt"
	"log/slog"
	"runtime/debug"
	"strings"
	"time"

	"errors"

	"github.com/go-git/go-git/v5"
	"github.com/go-git/go-git/v5/plumbing"
	"github.com/go-git/go-git/v5/plumbing/filemode"
	"github.com/go-git/go-git/v5/plumbing/object"
	"github.com/go-git/go-git/v5/plumbing/storer"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/metric"
)

var gitcacheMeter = otel.Meter("soad.gitcache")

var (
	operationDuration metric.Float64Histogram
	plainOpenDuration metric.Float64Histogram
)

func init() {
	var err error
	operationDuration, err = gitcacheMeter.Float64Histogram(
		"gitcache.operation.duration",
		metric.WithDescription("Duration of gitcache operations in milliseconds"),
		metric.WithUnit("ms"),
	)
	if err != nil {
		slog.Error("failed to create gitcache.operation.duration histogram", "error", err)
	}

	plainOpenDuration, err = gitcacheMeter.Float64Histogram(
		"gitcache.plainopen.duration",
		metric.WithDescription("Duration of git.PlainOpen calls in milliseconds"),
		metric.WithUnit("ms"),
	)
	if err != nil {
		slog.Error("failed to create gitcache.plainopen.duration histogram", "error", err)
	}
}

var (
	_ RepoReader = (*Manager)(nil)
	_ RepoReader = (*GoGitReader)(nil)
)

// GoGitReader implements RepoReader using go-git for read operations
// (GetCommitDetails, ComputeChangelog, ResolveSubmodules) while
// delegating clone/fetch to the shell-based Manager.
type GoGitReader struct {
	shell *Manager // delegate for EnsureCloned / ResolveRepo
}

// NewGoGitReader creates a GoGitReader that delegates clone/fetch to the
// given shell-based Manager.
func NewGoGitReader(shell *Manager) *GoGitReader {
	return &GoGitReader{shell: shell}
}

// EnsureCloned delegates to the shell-based Manager.
func (r *GoGitReader) EnsureCloned(ctx context.Context, repoURL string) (string, error) {
	return r.shell.EnsureCloned(ctx, repoURL)
}

// ResolveRepo delegates to the shell-based Manager.
func (r *GoGitReader) ResolveRepo(ctx context.Context, repoURL string) (string, error) {
	return r.shell.ResolveRepo(ctx, repoURL)
}

// plainOpen opens a bare repository at the given path and records the
// duration for the plainopen histogram.
func plainOpen(repoHandle string) (*git.Repository, error) {
	start := time.Now()
	repo, err := git.PlainOpen(repoHandle)
	elapsed := float64(time.Since(start).Milliseconds())
	if plainOpenDuration != nil {
		plainOpenDuration.Record(context.Background(), elapsed,
			metric.WithAttributes(attribute.String("repo", repoHandle)))
	}
	return repo, err
}

// GetCommitDetails reads full metadata for a single commit using go-git.
func (r *GoGitReader) GetCommitDetails(ctx context.Context, repoHandle, sha string) (details *CommitDetails, err error) {
	defer func() {
		if rec := recover(); rec != nil {
			err = fmt.Errorf("gitcache panic in GetCommitDetails: %v\n%s", rec, debug.Stack())
		}
	}()

	start := time.Now()

	repo, err := plainOpen(repoHandle)
	if err != nil {
		r.recordOp(start, "get_commit_details", repoHandle, "error")
		return nil, fmt.Errorf("opening repo %s: %w", repoHandle, err)
	}

	hash := plumbing.NewHash(sha)
	commit, err := repo.CommitObject(hash)
	if err != nil {
		r.recordOp(start, "get_commit_details", repoHandle, "error")
		return nil, fmt.Errorf("reading commit %s: %w", sha, err)
	}

	subject, body := splitMessage(commit.Message)
	details = &CommitDetails{
		Sha:         commit.Hash.String(),
		Message:     subject,
		Body:        body,
		AuthorName:  commit.Author.Name,
		AuthorEmail: commit.Author.Email,
		CommitDate:  commit.Author.When.Format("2006-01-02T15:04:05-07:00"),
	}

	r.recordOp(start, "get_commit_details", repoHandle, "ok")
	slog.InfoContext(ctx, "GetCommitDetails",
		"repo", repoHandle,
		"sha", sha,
		"duration_ms", time.Since(start).Milliseconds(),
		"implementation", "gogit",
	)
	return details, nil
}

// ComputeChangelog returns commits between fromSHA (exclusive) and toSHA
// (inclusive) using ancestor-set exclusion.
func (r *GoGitReader) ComputeChangelog(ctx context.Context, repoHandle, fromSHA, toSHA string) (commits []CommitDetails, truncated bool, err error) {
	defer func() {
		if rec := recover(); rec != nil {
			err = fmt.Errorf("gitcache panic in ComputeChangelog: %v\n%s", rec, debug.Stack())
		}
	}()

	start := time.Now()

	repo, err := plainOpen(repoHandle)
	if err != nil {
		r.recordOp(start, "compute_changelog", repoHandle, "error")
		return nil, false, fmt.Errorf("opening repo %s: %w", repoHandle, err)
	}

	fromHash := plumbing.NewHash(fromSHA)
	toHash := plumbing.NewHash(toSHA)

	// Build the exclusion set: all commits reachable from fromSHA.
	// Bounded at MaxChangelogCommits * 2 to keep memory finite while
	// covering any reasonable traversal window.
	excludeSet := make(map[plumbing.Hash]struct{})
	fromIter, err := repo.Log(&git.LogOptions{
		From:  fromHash,
		Order: git.LogOrderCommitterTime,
	})
	if err != nil {
		r.recordOp(start, "compute_changelog", repoHandle, "error")
		return nil, false, fmt.Errorf("building exclusion set from %s: %w", fromSHA, err)
	}
	const excludeLimit = MaxChangelogCommits * 2
	_ = fromIter.ForEach(func(c *object.Commit) error {
		excludeSet[c.Hash] = struct{}{}
		if len(excludeSet) >= excludeLimit {
			return storer.ErrStop
		}
		return nil
	})

	// Walk from toSHA, collecting commits not in the exclusion set.
	// Do NOT return ErrStop when hitting an excluded commit -- there may
	// be other reachable commits via a different parent path that are not
	// in the exclusion set.
	toIter, err := repo.Log(&git.LogOptions{
		From:  toHash,
		Order: git.LogOrderCommitterTime,
	})
	if err != nil {
		r.recordOp(start, "compute_changelog", repoHandle, "error")
		return nil, false, fmt.Errorf("walking changelog from %s: %w", toSHA, err)
	}

	// walkLimit caps total commits visited (not just collected) to prevent
	// unbounded traversal when fromSHA and toSHA are on unrelated histories.
	const walkLimit = MaxChangelogCommits * 4
	var result []CommitDetails
	visited := 0
	_ = toIter.ForEach(func(c *object.Commit) error {
		visited++
		if visited > walkLimit {
			return storer.ErrStop
		}
		if _, excluded := excludeSet[c.Hash]; excluded {
			return nil // skip but do not stop
		}
		result = append(result, commitToDetails(c))
		if len(result) > MaxChangelogCommits {
			return storer.ErrStop
		}
		return nil
	})

	if len(result) > MaxChangelogCommits {
		r.recordOp(start, "compute_changelog", repoHandle, "ok")
		slog.InfoContext(ctx, "ComputeChangelog",
			"repo", repoHandle,
			"from", fromSHA,
			"to", toSHA,
			"commits", MaxChangelogCommits,
			"truncated", true,
			"duration_ms", time.Since(start).Milliseconds(),
			"implementation", "gogit",
		)
		return result[:MaxChangelogCommits], true, nil
	}

	r.recordOp(start, "compute_changelog", repoHandle, "ok")
	slog.InfoContext(ctx, "ComputeChangelog",
		"repo", repoHandle,
		"from", fromSHA,
		"to", toSHA,
		"commits", len(result),
		"truncated", false,
		"duration_ms", time.Since(start).Milliseconds(),
		"implementation", "gogit",
	)
	return result, false, nil
}

// ResolveSubmodules returns submodule references at a given commit using go-git.
func (r *GoGitReader) ResolveSubmodules(ctx context.Context, repoHandle, sha string) (refs []SubmoduleRef, err error) {
	defer func() {
		if rec := recover(); rec != nil {
			err = fmt.Errorf("gitcache panic in ResolveSubmodules: %v\n%s", rec, debug.Stack())
		}
	}()

	start := time.Now()

	repo, err := plainOpen(repoHandle)
	if err != nil {
		r.recordOp(start, "resolve_submodules", repoHandle, "error")
		return nil, fmt.Errorf("opening repo %s: %w", repoHandle, err)
	}

	commit, err := repo.CommitObject(plumbing.NewHash(sha))
	if err != nil {
		r.recordOp(start, "resolve_submodules", repoHandle, "error")
		return nil, fmt.Errorf("reading commit %s: %w", sha, err)
	}

	tree, err := commit.Tree()
	if err != nil {
		r.recordOp(start, "resolve_submodules", repoHandle, "error")
		return nil, fmt.Errorf("reading tree for %s: %w", sha, err)
	}

	gitmodulesEntry, err := tree.File(".gitmodules")
	if errors.Is(err, object.ErrFileNotFound) {
		// No .gitmodules at this commit -- not an error, just no submodules.
		r.recordOp(start, "resolve_submodules", repoHandle, "ok")
		slog.InfoContext(ctx, "ResolveSubmodules",
			"repo", repoHandle,
			"sha", sha,
			"submodules", 0,
			"duration_ms", time.Since(start).Milliseconds(),
			"implementation", "gogit",
		)
		return nil, nil
	}
	if err != nil {
		r.recordOp(start, "resolve_submodules", repoHandle, "error")
		return nil, fmt.Errorf("reading .gitmodules at %s: %w", sha, err)
	}

	content, err := gitmodulesEntry.Contents()
	if err != nil {
		r.recordOp(start, "resolve_submodules", repoHandle, "error")
		return nil, fmt.Errorf("reading .gitmodules content at %s: %w", sha, err)
	}

	// Reuse existing parseGitmodules for .gitmodules blob content.
	// Note: parseGitmodules does not handle INI-style escaping (e.g.,
	// quoted/escaped path values). This is acceptable for SpongePowered repos.
	pathToURL := parseGitmodules([]byte(content))

	for _, entry := range tree.Entries {
		if entry.Mode == filemode.Submodule {
			if url, ok := pathToURL[entry.Name]; ok {
				refs = append(refs, SubmoduleRef{
					Path: entry.Name,
					URL:  url,
					Sha:  entry.Hash.String(),
				})
			}
		}
	}

	r.recordOp(start, "resolve_submodules", repoHandle, "ok")
	slog.InfoContext(ctx, "ResolveSubmodules",
		"repo", repoHandle,
		"sha", sha,
		"submodules", len(refs),
		"duration_ms", time.Since(start).Milliseconds(),
		"implementation", "gogit",
	)
	return refs, nil
}

// splitMessage splits a full commit message into subject (first line) and
// body (everything after the first newline, trimmed). This matches the
// convention of "subject = first line" used by git log --oneline.
func splitMessage(msg string) (subject, body string) {
	msg = strings.TrimSpace(msg)
	if idx := strings.Index(msg, "\n"); idx >= 0 {
		return msg[:idx], strings.TrimSpace(msg[idx+1:])
	}
	return msg, ""
}

// commitToDetails converts a go-git commit object to a CommitDetails struct
// for use in ComputeChangelog. Body is intentionally left empty to match the
// shell backend's ComputeChangelog, which uses git log --format=%s (subject only).
func commitToDetails(c *object.Commit) CommitDetails {
	subject, _ := splitMessage(c.Message)
	return CommitDetails{
		Sha:         c.Hash.String(),
		Message:     subject,
		AuthorName:  c.Author.Name,
		AuthorEmail: c.Author.Email,
		CommitDate:  c.Author.When.Format("2006-01-02T15:04:05-07:00"),
	}
}

// recordOp records the duration of a gitcache operation to the OTel histogram.
func (r *GoGitReader) recordOp(start time.Time, operation, repo, status string) {
	if operationDuration == nil {
		return
	}
	elapsed := float64(time.Since(start).Milliseconds())
	operationDuration.Record(context.Background(), elapsed,
		metric.WithAttributes(
			attribute.String("operation", operation),
			attribute.String("repo", repo),
			attribute.String("status", status),
			attribute.String("implementation", "gogit"),
		),
	)
}
