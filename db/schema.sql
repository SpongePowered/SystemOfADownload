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
