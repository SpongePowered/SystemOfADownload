-- Remove top-level commit URL
UPDATE artifact_versions
SET commit_body = commit_body - 'url'
WHERE commit_body ? 'url';

-- Remove submodule commit URLs
UPDATE artifact_versions
SET commit_body = jsonb_set(
    commit_body,
    '{submodules}',
    (
        SELECT jsonb_agg(elem - 'url')
        FROM jsonb_array_elements(commit_body->'submodules') AS elem
    )
)
WHERE commit_body->'submodules' IS NOT NULL
  AND jsonb_array_length(commit_body->'submodules') > 0;
