CREATE TABLE github_user_cache (
    author_email TEXT PRIMARY KEY,
    github_username TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- The predicate is duplicated verbatim by ListVersionsNeedingGitHubAuthorResolution.
-- The schema version is a literal because a partial index predicate cannot be
-- parameterised; bumping domain.AuthorResolutionSchema needs a new migration
-- that replaces this index. Guarded by TestAuthorResolutionSchemaMatchesSQL.
CREATE INDEX idx_versions_github_authors_unresolved
    ON artifact_versions (id DESC)
    WHERE commit_body->>'enrichedAt' IS NOT NULL
      AND (commit_body->'authorResolution'->>'schema') IS DISTINCT FROM '1';
