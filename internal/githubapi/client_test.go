package githubapi_test

import (
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"

	"github.com/spongepowered/systemofadownload/internal/githubapi"
)

func TestUsernameFromNoreplyEmail(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name  string
		email string
		want  string
	}{
		{name: "modern address", email: "12345+octocat@users.noreply.github.com", want: "octocat"},
		{name: "legacy address", email: "octocat@users.noreply.github.com", want: "octocat"},
		{name: "case insensitive domain", email: "octocat@USERS.NOREPLY.GITHUB.COM", want: "octocat"},
		{name: "ordinary email", email: "octocat@example.com"},
		{name: "invalid username", email: "-invalid@users.noreply.github.com"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			if got := githubapi.UsernameFromNoreplyEmail(tt.email); got != tt.want {
				t.Errorf("UsernameFromNoreplyEmail() = %q, want %q", got, tt.want)
			}
		})
	}
}

func TestParseRepository(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name      string
		rawURL    string
		wantOwner string
		wantRepo  string
		wantOK    bool
	}{
		{name: "HTTPS repository", rawURL: "https://github.com/SpongePowered/Sponge.git", wantOwner: "SpongePowered", wantRepo: "Sponge", wantOK: true},
		{name: "commit URL", rawURL: "https://github.com/SpongePowered/Sponge/commit/abc123", wantOwner: "SpongePowered", wantRepo: "Sponge", wantOK: true},
		{name: "SSH repository", rawURL: "git@github.com:SpongePowered/Sponge.git", wantOwner: "SpongePowered", wantRepo: "Sponge", wantOK: true},
		{name: "non GitHub repository", rawURL: "https://gitlab.com/example/project"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			owner, repo, ok := githubapi.ParseRepository(tt.rawURL)
			if owner != tt.wantOwner || repo != tt.wantRepo || ok != tt.wantOK {
				t.Errorf("ParseRepository() = (%q, %q, %v), want (%q, %q, %v)",
					owner, repo, ok, tt.wantOwner, tt.wantRepo, tt.wantOK)
			}
		})
	}
}

func TestClientResolveUsername(t *testing.T) {
	t.Parallel()

	var requests atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests.Add(1)
		if got := r.Header.Get("Authorization"); got != "Bearer test-token" {
			t.Errorf("Authorization = %q, want bearer token", got)
		}
		if r.URL.Path != "/repos/SpongePowered/Sponge/commits/abc123" {
			t.Errorf("path = %q", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = fmt.Fprint(w, `{"author":{"login":"octocat"}}`)
	}))
	defer server.Close()

	client := githubapi.NewClientWithBaseURL(server.Client(), "test-token", server.URL)
	username, err := client.ResolveUsername(
		t.Context(),
		"https://github.com/SpongePowered/Sponge",
		"abc123",
		"developer@example.com",
	)
	if err != nil {
		t.Fatalf("ResolveUsername() error = %v", err)
	}
	if username != "octocat" {
		t.Errorf("ResolveUsername() = %q, want octocat", username)
	}

	if got := requests.Load(); got != 1 {
		t.Errorf("request count = %d, want 1 request", got)
	}
}

func TestClientResolveUsernameFallbacks(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name       string
		statusCode int
		body       string
		want       string
		wantErr    bool
	}{
		{name: "unassociated commit", statusCode: http.StatusOK, body: `{"author":null}`},
		{name: "commit not found", statusCode: http.StatusNotFound},
		{name: "rate limited", statusCode: http.StatusForbidden, body: `{"message":"rate limit exceeded"}`, wantErr: true},
		{name: "secondary rate limited", statusCode: http.StatusTooManyRequests, body: `{"message":"abuse detection"}`, wantErr: true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
				if tt.name == "rate limited" {
					w.Header().Set("X-RateLimit-Reset", "2000000000")
				}
				// Secondary limits send Retry-After and no X-RateLimit-Reset.
				if tt.name == "secondary rate limited" {
					w.Header().Set("Retry-After", "60")
				}
				w.WriteHeader(tt.statusCode)
				_, _ = fmt.Fprint(w, tt.body)
			}))
			defer server.Close()

			client := githubapi.NewClientWithBaseURL(server.Client(), "", server.URL)
			got, err := client.ResolveUsername(
				t.Context(),
				"https://github.com/SpongePowered/Sponge",
				"abc123",
				"developer@example.com",
			)
			if (err != nil) != tt.wantErr {
				t.Fatalf("ResolveUsername() error = %v, wantErr %v", err, tt.wantErr)
			}
			if got != tt.want {
				t.Errorf("ResolveUsername() = %q, want %q", got, tt.want)
			}
			if tt.wantErr {
				var rateLimitErr *githubapi.RateLimitError
				if !errors.As(err, &rateLimitErr) {
					t.Fatalf("error = %T, want *githubapi.RateLimitError", err)
				}
			}
		})
	}
}
