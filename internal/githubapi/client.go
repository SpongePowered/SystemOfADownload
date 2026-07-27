package githubapi

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"path"
	"regexp"
	"strconv"
	"strings"
	"time"
)

const defaultBaseURL = "https://api.github.com"

var githubUsernamePattern = regexp.MustCompile(`^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$`)

// Client resolves Git commit authors to their associated GitHub usernames.
type Client struct {
	httpClient *http.Client
	baseURL    string
	token      string
}

// RateLimitError reports when GitHub asks callers to wait until a reset time.
type RateLimitError struct {
	ResetAt time.Time
	Message string
}

func (e *RateLimitError) Error() string {
	return e.Message
}

// NewClient creates a GitHub API client. The token is optional for public repositories.
func NewClient(httpClient *http.Client, token string) *Client {
	return NewClientWithBaseURL(httpClient, token, defaultBaseURL)
}

// NewClientWithBaseURL creates a client with a custom API base URL.
func NewClientWithBaseURL(httpClient *http.Client, token, baseURL string) *Client {
	if httpClient == nil {
		httpClient = http.DefaultClient
	}
	return &Client{
		httpClient: httpClient,
		baseURL:    strings.TrimRight(baseURL, "/"),
		token:      strings.TrimSpace(token),
	}
}

// ResolveUsername returns the GitHub account associated with a commit author.
// An empty username with a nil error means GitHub has no associated account.
func (c *Client) ResolveUsername(ctx context.Context, repoURL, sha, email string) (string, error) {
	if username := UsernameFromNoreplyEmail(email); username != "" {
		return username, nil
	}

	owner, repo, ok := ParseRepository(repoURL)
	if !ok || strings.TrimSpace(sha) == "" {
		return "", nil
	}

	endpoint := fmt.Sprintf(
		"%s/repos/%s/%s/commits/%s",
		c.baseURL,
		url.PathEscape(owner),
		url.PathEscape(repo),
		url.PathEscape(sha),
	)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, http.NoBody)
	if err != nil {
		return "", fmt.Errorf("creating GitHub commit request: %w", err)
	}
	req.Header.Set("Accept", "application/vnd.github+json")
	req.Header.Set("User-Agent", "SystemOfADownload")
	req.Header.Set("X-GitHub-Api-Version", "2022-11-28")
	if c.token != "" {
		req.Header.Set("Authorization", "Bearer "+c.token)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("requesting GitHub commit: %w", err)
	}
	defer func() {
		_ = resp.Body.Close()
	}()

	if resp.StatusCode == http.StatusNotFound {
		return "", nil
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 4<<10))
		message := fmt.Sprintf("GitHub commit lookup returned %s: %s", resp.Status, strings.TrimSpace(string(body)))
		if resp.StatusCode == http.StatusForbidden || resp.StatusCode == http.StatusTooManyRequests {
			if reset, err := strconv.ParseInt(resp.Header.Get("X-RateLimit-Reset"), 10, 64); err == nil {
				return "", &RateLimitError{
					ResetAt: time.Unix(reset, 0),
					Message: message,
				}
			}
		}
		return "", fmt.Errorf("%s", message)
	}

	var result struct {
		Author *struct {
			Login string `json:"login"`
		} `json:"author"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return "", fmt.Errorf("decoding GitHub commit response: %w", err)
	}

	username := ""
	if result.Author != nil {
		username = result.Author.Login
	}
	return username, nil
}

// UsernameFromNoreplyEmail extracts a username from GitHub's noreply address formats.
func UsernameFromNoreplyEmail(email string) string {
	parts := strings.Split(strings.TrimSpace(email), "@")
	if len(parts) != 2 || !strings.EqualFold(parts[1], "users.noreply.github.com") {
		return ""
	}

	username := parts[0]
	if _, suffix, found := strings.Cut(username, "+"); found {
		username = suffix
	}
	if !githubUsernamePattern.MatchString(username) {
		return ""
	}
	return username
}

// ParseRepository extracts an owner and repository from a GitHub repository or commit URL.
func ParseRepository(rawURL string) (owner, repo string, ok bool) {
	rawURL = strings.TrimSpace(rawURL)
	if strings.HasPrefix(rawURL, "git@github.com:") {
		rawURL = "https://github.com/" + strings.TrimPrefix(rawURL, "git@github.com:")
	}

	parsed, err := url.Parse(rawURL)
	if err != nil || !strings.EqualFold(parsed.Hostname(), "github.com") {
		return "", "", false
	}

	segments := strings.Split(strings.Trim(path.Clean(parsed.Path), "/"), "/")
	if len(segments) < 2 {
		return "", "", false
	}

	owner = segments[0]
	repo = strings.TrimSuffix(segments[1], ".git")
	if owner == "" || repo == "" {
		return "", "", false
	}
	return owner, repo, true
}
