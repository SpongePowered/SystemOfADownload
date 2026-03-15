-- name: GetGroup :one
SELECT * FROM groups
WHERE maven_id = $1;

-- name: GroupExistsByMavenID :one
SELECT EXISTS(SELECT 1 FROM groups WHERE LOWER(maven_id) = LOWER($1)) AS exists;

-- name: ListGroups :many
SELECT * FROM groups;

-- name: ListArtifactsByGroup :many
SELECT * FROM artifacts
WHERE group_id = $1;

-- name: GetArtifactByGroupAndId :one
SELECT * FROM artifacts
WHERE group_id = $1 AND artifact_id = $2;

-- name: GetArtifactVersionSchema :one
SELECT version_schema FROM artifacts
WHERE group_id = $1 AND artifact_id = $2;

-- name: ListArtifactVersions :many
SELECT av.*
FROM artifact_versions av
JOIN artifacts a ON av.artifact_id = a.id
WHERE a.group_id = $1 AND a.artifact_id = $2
ORDER BY av.sort_order DESC;

-- name: UpdateArtifactVersionOrder :exec
UPDATE artifact_versions
SET sort_order = $2
WHERE id = $1;

-- name: GetArtifactVersion :one
SELECT av.*
FROM artifact_versions av
JOIN artifacts a ON av.artifact_id = a.id
WHERE a.group_id = $1 AND a.artifact_id = $2 AND av.version = $3;

-- name: ListArtifactVersionAssets :many
SELECT * FROM artifact_versioned_assets
WHERE artifact_version_id = $1;

-- name: ListArtifactVersionTags :many
SELECT * FROM artifact_versioned_tags
WHERE artifact_version_id = $1;

-- name: CreateGroup :one
INSERT INTO groups (maven_id, name, website)
VALUES ($1, $2, $3)
ON CONFLICT (maven_id) DO UPDATE SET
    name = EXCLUDED.name,
    website = EXCLUDED.website
RETURNING *;

-- name: CreateArtifact :one
INSERT INTO artifacts (group_id, artifact_id, name, website, issues, git_repositories, version_schema)
VALUES ($1, $2, $3, $4, $5, $6, $7)
ON CONFLICT (group_id, artifact_id) DO UPDATE SET
    name = EXCLUDED.name,
    website = EXCLUDED.website,
    issues = EXCLUDED.issues,
    git_repositories = EXCLUDED.git_repositories,
    version_schema = EXCLUDED.version_schema
RETURNING *;

-- name: CreateArtifactVersion :one
INSERT INTO artifact_versions (artifact_id, version, sort_order, commit_body)
VALUES ($1, $2, $3, $4)
ON CONFLICT (artifact_id, version) DO UPDATE SET
    sort_order = EXCLUDED.sort_order,
    commit_body = EXCLUDED.commit_body
RETURNING *;

-- name: CreateArtifactVersionAsset :one
INSERT INTO artifact_versioned_assets (artifact_version_id, classifier, sha256, download_url)
VALUES ($1, $2, $3, $4)
RETURNING *;

-- name: ListArtifactVersionStringsByArtifactID :many
SELECT av.version
FROM artifact_versions av
WHERE av.artifact_id = $1
ORDER BY av.version
LIMIT $2 OFFSET $3;

-- name: UpdateArtifactVersionCommitBody :exec
UPDATE artifact_versions
SET commit_body = $2
WHERE id = $1;

-- name: CreateArtifactVersionTag :one
INSERT INTO artifact_versioned_tags (artifact_version_id, tag_key, tag_value)
VALUES ($1, $2, $3)
ON CONFLICT (artifact_version_id, tag_key) DO UPDATE SET
    tag_value = EXCLUDED.tag_value
RETURNING *;

-- name: DeleteArtifactVersionTags :exec
DELETE FROM artifact_versioned_tags
WHERE artifact_version_id = $1;

-- name: ListDistinctTagsByArtifact :many
SELECT DISTINCT t.tag_key, t.tag_value
FROM artifact_versioned_tags t
JOIN artifact_versions av ON t.artifact_version_id = av.id
JOIN artifacts a ON av.artifact_id = a.id
WHERE a.group_id = $1 AND a.artifact_id = $2
ORDER BY t.tag_key, t.tag_value;
