CREATE TABLE github_user_cache (
    author_email TEXT PRIMARY KEY,
    github_username TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_github_user_cache_expires_at
    ON github_user_cache (expires_at);

CREATE INDEX idx_versions_github_authors_unresolved
    ON artifact_versions (id DESC)
    WHERE commit_body->>'enrichedAt' IS NOT NULL
      AND commit_body->>'githubAuthorsResolvedAt' IS NULL;
