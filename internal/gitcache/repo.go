package gitcache

import "context"

// RepoReader abstracts read and sync operations on bare git repositories.
// Implementations can be swapped between shell-based and pure-Go (go-git)
// backends.
//
// The repoHandle parameter is intentionally opaque. Callers must not
// interpret it as a filesystem path or any other concrete type, even
// though the current implementations use a filesystem path internally.
// Each read method opens the repository fresh from the handle; handles
// do not represent live repository references.
type RepoReader interface {
	// EnsureCloned clones a bare repo (or fetches if it already exists).
	// Returns an opaque handle identifying the repo for subsequent calls.
	EnsureCloned(ctx context.Context, repoURL string) (repoHandle string, err error)

	// ResolveRepo returns the handle for an already-cloned repo without
	// network I/O. Returns an error if the repo has not been cloned.
	ResolveRepo(ctx context.Context, repoURL string) (repoHandle string, err error)

	// GetCommitDetails reads full metadata for a single commit.
	GetCommitDetails(ctx context.Context, repoHandle, sha string) (*CommitDetails, error)

	// ResolveSubmodules returns submodule references at a given commit.
	// Returns nil, nil if the commit has no .gitmodules.
	ResolveSubmodules(ctx context.Context, repoHandle, sha string) ([]SubmoduleRef, error)

	// ComputeChangelog returns commits between fromSHA (exclusive) and toSHA
	// (inclusive), in reverse chronological order, capped at MaxChangelogCommits.
	ComputeChangelog(ctx context.Context, repoHandle string, fromSHA, toSHA string) (commits []CommitDetails, truncated bool, err error)
}
