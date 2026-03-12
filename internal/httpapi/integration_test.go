package httpapi_test

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/spongepowered/systemofadownload/api"
	"github.com/spongepowered/systemofadownload/internal/app"
	"github.com/spongepowered/systemofadownload/internal/httpapi"
	"github.com/spongepowered/systemofadownload/internal/repository"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
	"github.com/testcontainers/testcontainers-go/wait"
)

const schema = `
CREATE TABLE groups (
    maven_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    website TEXT
);

CREATE TABLE artifacts (
    id BIGSERIAL PRIMARY KEY,
    group_id TEXT NOT NULL REFERENCES groups(maven_id),
    artifact_id TEXT NOT NULL,
    name TEXT NOT NULL,
    website TEXT,
    git_repositories JSONB NOT NULL DEFAULT '[]',
    UNIQUE(group_id, artifact_id)
);

CREATE TABLE artifact_versions (
    id BIGSERIAL PRIMARY KEY,
    artifact_id BIGINT NOT NULL REFERENCES artifacts(id),
    version TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    commit_body JSONB,
    UNIQUE(artifact_id, version)
);

CREATE TABLE artifact_versioned_assets (
    id BIGSERIAL PRIMARY KEY,
    artifact_version_id BIGINT NOT NULL REFERENCES artifact_versions(id),
    classifier TEXT,
    sha256 TEXT,
    download_url TEXT NOT NULL
);

CREATE TABLE artifact_versioned_tags (
    artifact_version_id BIGINT NOT NULL REFERENCES artifact_versions(id),
    tag_key TEXT NOT NULL,
    tag_value TEXT NOT NULL,
    PRIMARY KEY (artifact_version_id, tag_key)
);
`

// setupTestServer creates a PostgreSQL testcontainer, applies the schema,
// and returns an httptest.Server wired to the full application stack.
func setupTestServer(t *testing.T) *httptest.Server {
	t.Helper()
	ctx := t.Context()

	pgContainer, err := postgres.Run(ctx,
		"postgres:16-alpine",
		postgres.WithDatabase("testdb"),
		postgres.WithUsername("testuser"),
		postgres.WithPassword("testpass"),
		testcontainers.WithWaitStrategy(
			wait.ForLog("database system is ready to accept connections").
				WithOccurrence(2).
				WithStartupTimeout(30*time.Second),
		),
	)
	if err != nil {
		t.Fatalf("failed to start postgres container: %v", err)
	}
	t.Cleanup(func() {
		if err := pgContainer.Terminate(ctx); err != nil {
			t.Logf("failed to terminate postgres container: %v", err)
		}
	})

	connStr, err := pgContainer.ConnectionString(ctx, "sslmode=disable")
	if err != nil {
		t.Fatalf("failed to get connection string: %v", err)
	}

	pool, err := pgxpool.New(ctx, connStr)
	if err != nil {
		t.Fatalf("failed to create connection pool: %v", err)
	}
	t.Cleanup(func() { pool.Close() })

	// Apply schema
	if _, err := pool.Exec(ctx, schema); err != nil {
		t.Fatalf("failed to apply schema: %v", err)
	}

	repo := repository.NewRepository(pool)
	service := app.NewService(repo)
	handler := httpapi.NewHandler(service, nil)
	apiHandler := api.NewStrictHandler(handler, nil)
	mux := http.NewServeMux()
	httpHandler := api.HandlerFromMux(apiHandler, mux)

	return httptest.NewServer(httpHandler)
}

func TestIntegration_GroupAndArtifactLifecycle(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}

	server := setupTestServer(t)
	t.Cleanup(server.Close)

	client := server.Client()
	baseURL := server.URL

	website := "https://spongepowered.org"

	// Step 1: Register a group
	t.Run("register group", func(t *testing.T) {
		body := api.Group{
			GroupCoordinates: "org.spongepowered",
			Name:             "SpongePowered",
			Website:          &website,
		}
		resp := doJSON(t, client, http.MethodPost, baseURL+"/groups", body)
		defer resp.Body.Close()

		if resp.StatusCode != http.StatusCreated {
			t.Fatalf("expected status 201, got %d", resp.StatusCode)
		}

		var got api.Group
		if err := json.NewDecoder(resp.Body).Decode(&got); err != nil {
			t.Fatalf("failed to decode response: %v", err)
		}

		if got.GroupCoordinates != "org.spongepowered" {
			t.Errorf("expected groupCoordinates %q, got %q", "org.spongepowered", got.GroupCoordinates)
		}
		if got.Name != "SpongePowered" {
			t.Errorf("expected name %q, got %q", "SpongePowered", got.Name)
		}
		if got.Website == nil || *got.Website != website {
			t.Errorf("expected website %q, got %v", website, got.Website)
		}
	})

	// Step 2: Get the group
	t.Run("get group", func(t *testing.T) {
		resp, err := client.Get(baseURL + "/groups/org.spongepowered")
		if err != nil {
			t.Fatalf("failed to get group: %v", err)
		}
		defer resp.Body.Close()

		if resp.StatusCode != http.StatusOK {
			t.Fatalf("expected status 200, got %d", resp.StatusCode)
		}

		var got api.Group
		if err := json.NewDecoder(resp.Body).Decode(&got); err != nil {
			t.Fatalf("failed to decode response: %v", err)
		}

		if got.GroupCoordinates != "org.spongepowered" {
			t.Errorf("expected groupCoordinates %q, got %q", "org.spongepowered", got.GroupCoordinates)
		}
		if got.Name != "SpongePowered" {
			t.Errorf("expected name %q, got %q", "SpongePowered", got.Name)
		}
	})

	// Step 3: Get artifacts for the group (should be empty)
	t.Run("get artifacts empty", func(t *testing.T) {
		resp, err := client.Get(baseURL + "/groups/org.spongepowered/artifacts")
		if err != nil {
			t.Fatalf("failed to get artifacts: %v", err)
		}
		defer resp.Body.Close()

		if resp.StatusCode != http.StatusOK {
			t.Fatalf("expected status 200, got %d", resp.StatusCode)
		}

		var got api.GroupArtifacts
		if err := json.NewDecoder(resp.Body).Decode(&got); err != nil {
			t.Fatalf("failed to decode response: %v", err)
		}

		if len(got.ArtifactIds) != 0 {
			t.Errorf("expected 0 artifacts, got %d", len(got.ArtifactIds))
		}
	})

	// Step 4: Register an artifact
	t.Run("register artifact", func(t *testing.T) {
		artifactWebsite := "https://github.com/SpongePowered/Sponge"
		body := api.ArtifactRegistration{
			ArtifactId:    "spongevanilla",
			DisplayName:   "SpongeVanilla",
			GitRepository: []string{"https://github.com/SpongePowered/Sponge"},
			Website:       &artifactWebsite,
		}
		resp := doJSON(t, client, http.MethodPost, baseURL+"/groups/org.spongepowered/artifacts", body)
		defer resp.Body.Close()

		if resp.StatusCode != http.StatusCreated {
			t.Fatalf("expected status 201, got %d", resp.StatusCode)
		}

		var got api.Artifact
		if err := json.NewDecoder(resp.Body).Decode(&got); err != nil {
			t.Fatalf("failed to decode response: %v", err)
		}

		if got.Coordinates.GroupId != "org.spongepowered" {
			t.Errorf("expected groupId %q, got %q", "org.spongepowered", got.Coordinates.GroupId)
		}
		if got.Coordinates.ArtifactId != "spongevanilla" {
			t.Errorf("expected artifactId %q, got %q", "spongevanilla", got.Coordinates.ArtifactId)
		}
	})

	// Step 5: Get artifacts again (should have one)
	t.Run("get artifacts after registration", func(t *testing.T) {
		resp, err := client.Get(baseURL + "/groups/org.spongepowered/artifacts")
		if err != nil {
			t.Fatalf("failed to get artifacts: %v", err)
		}
		defer resp.Body.Close()

		if resp.StatusCode != http.StatusOK {
			t.Fatalf("expected status 200, got %d", resp.StatusCode)
		}

		var got api.GroupArtifacts
		if err := json.NewDecoder(resp.Body).Decode(&got); err != nil {
			t.Fatalf("failed to decode response: %v", err)
		}

		if len(got.ArtifactIds) != 1 {
			t.Fatalf("expected 1 artifact, got %d", len(got.ArtifactIds))
		}
		if got.ArtifactIds[0] != "spongevanilla" {
			t.Errorf("expected artifactId %q, got %q", "spongevanilla", got.ArtifactIds[0])
		}
	})

	// Step 6: Verify duplicate artifact registration returns 409
	t.Run("register duplicate artifact returns 409", func(t *testing.T) {
		body := api.ArtifactRegistration{
			ArtifactId:    "spongevanilla",
			DisplayName:   "SpongeVanilla",
			GitRepository: []string{"https://github.com/SpongePowered/Sponge"},
		}
		resp := doJSON(t, client, http.MethodPost, baseURL+"/groups/org.spongepowered/artifacts", body)
		defer resp.Body.Close()

		if resp.StatusCode != http.StatusConflict {
			t.Fatalf("expected status 409, got %d", resp.StatusCode)
		}
	})

	// Step 7: Verify registering artifact for non-existent group returns 404
	t.Run("register artifact for non-existent group returns 404", func(t *testing.T) {
		body := api.ArtifactRegistration{
			ArtifactId:    "someartifact",
			DisplayName:   "Some Artifact",
			GitRepository: []string{},
		}
		resp := doJSON(t, client, http.MethodPost, baseURL+"/groups/com.nonexistent/artifacts", body)
		defer resp.Body.Close()

		if resp.StatusCode != http.StatusNotFound {
			t.Fatalf("expected status 404, got %d", resp.StatusCode)
		}
	})

	// Step 8: List all groups
	t.Run("list groups includes registered group", func(t *testing.T) {
		resp, err := client.Get(baseURL + "/groups")
		if err != nil {
			t.Fatalf("failed to list groups: %v", err)
		}
		defer resp.Body.Close()

		if resp.StatusCode != http.StatusOK {
			t.Fatalf("expected status 200, got %d", resp.StatusCode)
		}

		var got api.Groups
		if err := json.NewDecoder(resp.Body).Decode(&got); err != nil {
			t.Fatalf("failed to decode response: %v", err)
		}

		if len(got.Groups) != 1 {
			t.Fatalf("expected 1 group, got %d", len(got.Groups))
		}
		if got.Groups[0].GroupCoordinates != "org.spongepowered" {
			t.Errorf("expected groupCoordinates %q, got %q", "org.spongepowered", got.Groups[0].GroupCoordinates)
		}
	})
}

// doJSON marshals body to JSON and sends an HTTP request, returning the response.
func doJSON(t *testing.T, client *http.Client, method, url string, body any) *http.Response {
	t.Helper()
	jsonBody, err := json.Marshal(body)
	if err != nil {
		t.Fatalf("failed to marshal request body: %v", err)
	}

	req, err := http.NewRequest(method, url, bytes.NewReader(jsonBody))
	if err != nil {
		t.Fatalf("failed to create request: %v", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := client.Do(req)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	return resp
}
