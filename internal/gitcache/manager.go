package gitcache

import (
	"bufio"
	"bytes"
	"context"
	"fmt"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
)

// CommitDetails holds full metadata for a single git commit.
type CommitDetails struct {
	Sha         string
	Message     string
	Body        string
	AuthorName  string
	AuthorEmail string
	CommitDate  string // ISO 8601
}

// SubmoduleRef represents a submodule pointer at a specific commit.
type SubmoduleRef struct {
	Path string // submodule path in the repo
	URL  string // submodule remote URL
	Sha  string // commit SHA the submodule points to
}

// Manager manages bare git clones on the local filesystem with per-repo locking.
// Write operations (clone, fetch) take an exclusive lock. Read operations
// (git show, git log, git ls-tree) take a shared lock and run concurrently.
type Manager struct {
	baseDir string
	locks   sync.Map // normalized repo URL → *sync.RWMutex
}

// NewManager creates a Manager that stores bare clones under baseDir.
func NewManager(baseDir string) *Manager {
	return &Manager{baseDir: baseDir}
}

// repoLock returns the RWMutex for the given repo URL, creating one if needed.
func (m *Manager) repoLock(repoURL string) *sync.RWMutex {
	actual, _ := m.locks.LoadOrStore(repoURL, &sync.RWMutex{})
	return actual.(*sync.RWMutex)
}

// repoPath returns the local filesystem path for a bare clone of the given URL.
// Layout: baseDir/<host>/<path>.git
func (m *Manager) repoPath(repoURL string) (string, error) {
	u, err := url.Parse(repoURL)
	if err != nil {
		return "", fmt.Errorf("parsing repo URL %q: %w", repoURL, err)
	}

	host := u.Host
	if host == "" {
		host = "unknown"
	}
	path := strings.TrimPrefix(u.Path, "/")
	path = strings.TrimSuffix(path, ".git")

	return filepath.Join(m.baseDir, host, path+".git"), nil
}

// EnsureCloned clones the repo as a bare clone if it doesn't exist, or fetches
// all refs if it does. Returns the local path to the bare repo.
func (m *Manager) EnsureCloned(ctx context.Context, repoURL string) (string, error) {
	localPath, err := m.repoPath(repoURL)
	if err != nil {
		return "", err
	}

	mu := m.repoLock(repoURL)
	mu.Lock()
	defer mu.Unlock()

	if _, err := os.Stat(filepath.Join(localPath, "HEAD")); os.IsNotExist(err) {
		// Clone bare
		if err := os.MkdirAll(filepath.Dir(localPath), 0o755); err != nil {
			return "", fmt.Errorf("creating parent directory: %w", err)
		}
		cmd := exec.CommandContext(ctx, "git", "clone", "--bare", repoURL, localPath)
		if out, err := cmd.CombinedOutput(); err != nil {
			return "", fmt.Errorf("cloning %s: %w\n%s", repoURL, err, out)
		}
	} else {
		// Fetch all
		cmd := exec.CommandContext(ctx, "git", "-C", localPath, "fetch", "--all", "--prune")
		if out, err := cmd.CombinedOutput(); err != nil {
			return "", fmt.Errorf("fetching %s: %w\n%s", repoURL, err, out)
		}
	}

	return localPath, nil
}

// ResolveRepo returns the local path for an already-cloned repo without
// fetching. Returns an error if the repo hasn't been cloned yet. Use this
// for read-only operations when the repo is known to be up-to-date.
func (m *Manager) ResolveRepo(ctx context.Context, repoURL string) (string, error) {
	localPath, err := m.repoPath(repoURL)
	if err != nil {
		return "", err
	}

	if _, err := os.Stat(filepath.Join(localPath, "HEAD")); os.IsNotExist(err) {
		return "", fmt.Errorf("repo not cloned: %s", repoURL)
	}

	return localPath, nil
}

// GetCommitDetails reads full commit metadata for the given SHA.
func (m *Manager) GetCommitDetails(ctx context.Context, repoPath, sha string) (*CommitDetails, error) {
	// Format: SHA\nsubject\nauthor name\nauthor email\ndate\n\nbody
	format := "%H%n%s%n%aN%n%aE%n%aI"
	cmd := exec.CommandContext(ctx, "git", "-C", repoPath, "show", "-s", "--format="+format, sha)
	out, err := cmd.Output()
	if err != nil {
		return nil, fmt.Errorf("git show %s: %w", sha, err)
	}

	lines := strings.SplitN(strings.TrimRight(string(out), "\n"), "\n", 5)
	if len(lines) < 5 {
		return nil, fmt.Errorf("unexpected git show output for %s: got %d lines", sha, len(lines))
	}

	// Get the body separately (everything after the subject line)
	bodyCmd := exec.CommandContext(ctx, "git", "-C", repoPath, "show", "-s", "--format=%b", sha)
	bodyOut, err := bodyCmd.Output()
	if err != nil {
		return nil, fmt.Errorf("git show body %s: %w", sha, err)
	}

	return &CommitDetails{
		Sha:         lines[0],
		Message:     lines[1],
		AuthorName:  lines[2],
		AuthorEmail: lines[3],
		CommitDate:  lines[4],
		Body:        strings.TrimSpace(string(bodyOut)),
	}, nil
}

// ResolveSubmodules parses .gitmodules and git ls-tree at the given SHA to
// find submodule URLs and their pinned commit SHAs.
func (m *Manager) ResolveSubmodules(ctx context.Context, repoPath, sha string) ([]SubmoduleRef, error) {
	// Try to read .gitmodules at the given commit
	cmd := exec.CommandContext(ctx, "git", "-C", repoPath, "show", sha+":.gitmodules")
	gitmodulesOut, err := cmd.Output()
	if err != nil {
		// No .gitmodules at this commit — not an error, just no submodules
		return nil, nil //nolint:nilerr // intentional: missing .gitmodules means no submodules
	}

	// Parse .gitmodules INI-like format to get path → URL mapping
	pathToURL := parseGitmodules(gitmodulesOut)
	if len(pathToURL) == 0 {
		return nil, nil
	}

	// Use git ls-tree to get the commit SHAs for each submodule path
	lsCmd := exec.CommandContext(ctx, "git", "-C", repoPath, "ls-tree", sha)
	lsOut, err := lsCmd.Output()
	if err != nil {
		return nil, fmt.Errorf("git ls-tree %s: %w", sha, err)
	}

	var refs []SubmoduleRef
	scanner := bufio.NewScanner(bytes.NewReader(lsOut))
	for scanner.Scan() {
		line := scanner.Text()
		// Format: <mode> <type> <sha>\t<path>
		parts := strings.SplitN(line, "\t", 2)
		if len(parts) != 2 {
			continue
		}
		path := parts[1]
		fields := strings.Fields(parts[0])
		if len(fields) < 3 {
			continue
		}
		objType := fields[1]
		objSHA := fields[2]

		// Submodules appear as "commit" type entries
		if objType != "commit" {
			continue
		}

		if repoURL, ok := pathToURL[path]; ok {
			refs = append(refs, SubmoduleRef{
				Path: path,
				URL:  repoURL,
				Sha:  objSHA,
			})
		}
	}

	return refs, scanner.Err()
}

// MaxChangelogCommits is the maximum number of commits returned by ComputeChangelog.
// Prevents unbounded payloads for large version gaps.
const MaxChangelogCommits = 500

// ComputeChangelog returns the list of commits between fromSHA (exclusive) and
// toSHA (inclusive) in reverse chronological order, capped at MaxChangelogCommits.
// Returns truncated=true if there were more commits than the cap.
func (m *Manager) ComputeChangelog(ctx context.Context, repoPath, fromSHA, toSHA string) (commits []CommitDetails, truncated bool, err error) {
	format := "%H%n%s%n%aN%n%aE%n%aI"
	rangeSpec := fromSHA + ".." + toSHA
	// Request one extra to detect truncation
	maxCount := fmt.Sprintf("--max-count=%d", MaxChangelogCommits+1)
	cmd := exec.CommandContext(ctx, "git", "-C", repoPath, "log", maxCount, "--format="+format, rangeSpec)
	out, outErr := cmd.Output()
	if outErr != nil {
		return nil, false, fmt.Errorf("git log %s: %w", rangeSpec, outErr)
	}

	text := strings.TrimRight(string(out), "\n")
	if text == "" {
		return nil, false, nil
	}

	lines := strings.Split(text, "\n")
	// Each commit produces exactly 5 lines
	if len(lines)%5 != 0 {
		return nil, false, fmt.Errorf("unexpected git log output: %d lines (not multiple of 5)", len(lines))
	}

	result := make([]CommitDetails, 0, len(lines)/5)
	for i := 0; i < len(lines); i += 5 {
		result = append(result, CommitDetails{
			Sha:         lines[i],
			Message:     lines[i+1],
			AuthorName:  lines[i+2],
			AuthorEmail: lines[i+3],
			CommitDate:  lines[i+4],
		})
	}

	if len(result) > MaxChangelogCommits {
		return result[:MaxChangelogCommits], true, nil
	}
	return result, false, nil
}

// parseGitmodules parses a .gitmodules file and returns a map of path → URL.
func parseGitmodules(data []byte) map[string]string {
	pathToURL := make(map[string]string)

	var currentPath, currentURL string
	scanner := bufio.NewScanner(bytes.NewReader(data))
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())

		if strings.HasPrefix(line, "[submodule ") {
			// Flush previous entry
			if currentPath != "" && currentURL != "" {
				pathToURL[currentPath] = currentURL
			}
			currentPath = ""
			currentURL = ""
			continue
		}

		if idx := strings.IndexByte(line, '='); idx > 0 {
			key := strings.TrimSpace(line[:idx])
			value := strings.TrimSpace(line[idx+1:])
			switch key {
			case "path":
				currentPath = value
			case "url":
				currentURL = value
			}
		}
	}
	// Flush last entry
	if currentPath != "" && currentURL != "" {
		pathToURL[currentPath] = currentURL
	}

	return pathToURL
}
