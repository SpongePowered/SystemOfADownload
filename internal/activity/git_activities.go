package activity

import (
	"context"

	"github.com/spongepowered/systemofadownload/internal/gitcache"
)

// GitActivities provides local activities for git operations.
// These must be registered as local activities on the Temporal worker
// since they depend on worker-local filesystem state (the git cache).
type GitActivities struct {
	Cache *gitcache.Manager
}

// EnsureRepoClonedInput is the input for EnsureRepoCloned.
type EnsureRepoClonedInput struct {
	RepoURL string
}

// EnsureRepoClonedOutput is the output for EnsureRepoCloned.
type EnsureRepoClonedOutput struct {
	LocalPath string
}

// EnsureRepoCloned clones a bare repo if absent, or fetches all refs if it exists.
func (a *GitActivities) EnsureRepoCloned(ctx context.Context, input EnsureRepoClonedInput) (*EnsureRepoClonedOutput, error) {
	path, err := a.Cache.EnsureCloned(ctx, input.RepoURL)
	if err != nil {
		return nil, err
	}
	return &EnsureRepoClonedOutput{LocalPath: path}, nil
}

// GetCommitDetailsInput is the input for GetCommitDetails.
type GetCommitDetailsInput struct {
	RepoPath string
	Sha      string
}

// GetCommitDetailsOutput holds full commit metadata.
type GetCommitDetailsOutput struct {
	Sha         string
	Message     string
	Body        string
	AuthorName  string
	AuthorEmail string
	CommitDate  string
}

// GetCommitDetails resolves full commit metadata for a SHA.
func (a *GitActivities) GetCommitDetails(ctx context.Context, input GetCommitDetailsInput) (*GetCommitDetailsOutput, error) {
	details, err := a.Cache.GetCommitDetails(ctx, input.RepoPath, input.Sha)
	if err != nil {
		return nil, err
	}
	return &GetCommitDetailsOutput{
		Sha:         details.Sha,
		Message:     details.Message,
		Body:        details.Body,
		AuthorName:  details.AuthorName,
		AuthorEmail: details.AuthorEmail,
		CommitDate:  details.CommitDate,
	}, nil
}

// ResolveSubmodulesInput is the input for ResolveSubmodules.
type ResolveSubmodulesInput struct {
	RepoPath string
	Sha      string
}

// ResolveSubmodulesOutput holds the resolved submodule references.
type ResolveSubmodulesOutput struct {
	Submodules []SubmoduleRefOutput
}

// SubmoduleRefOutput is a resolved submodule pointer.
type SubmoduleRefOutput struct {
	Path string
	URL  string
	Sha  string
}

// ResolveSubmodules parses .gitmodules and git ls-tree to find submodule SHAs.
func (a *GitActivities) ResolveSubmodules(ctx context.Context, input ResolveSubmodulesInput) (*ResolveSubmodulesOutput, error) {
	refs, err := a.Cache.ResolveSubmodules(ctx, input.RepoPath, input.Sha)
	if err != nil {
		return nil, err
	}
	out := make([]SubmoduleRefOutput, len(refs))
	for i, ref := range refs {
		out[i] = SubmoduleRefOutput{
			Path: ref.Path,
			URL:  ref.URL,
			Sha:  ref.Sha,
		}
	}
	return &ResolveSubmodulesOutput{Submodules: out}, nil
}

// ComputeChangelogInput is the input for ComputeChangelog.
type ComputeChangelogInput struct {
	RepoPath string
	FromSHA  string
	ToSHA    string
}

// ComputeChangelogOutput holds the commit list between two SHAs.
type ComputeChangelogOutput struct {
	Commits []ChangelogCommit
}

// ChangelogCommit is a commit entry in a changelog.
type ChangelogCommit struct {
	Sha         string
	Message     string
	AuthorName  string
	AuthorEmail string
	CommitDate  string
}

// ComputeChangelog returns the list of commits between fromSHA and toSHA.
func (a *GitActivities) ComputeChangelog(ctx context.Context, input ComputeChangelogInput) (*ComputeChangelogOutput, error) {
	commits, err := a.Cache.ComputeChangelog(ctx, input.RepoPath, input.FromSHA, input.ToSHA)
	if err != nil {
		return nil, err
	}
	out := make([]ChangelogCommit, len(commits))
	for i, c := range commits {
		out[i] = ChangelogCommit{
			Sha:         c.Sha,
			Message:     c.Message,
			AuthorName:  c.AuthorName,
			AuthorEmail: c.AuthorEmail,
			CommitDate:  c.CommitDate,
		}
	}
	return &ComputeChangelogOutput{Commits: out}, nil
}
