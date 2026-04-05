-- Backfill commit URLs on enriched versions from existing repository + sha.
-- Strips trailing '/' and '.git' from the repository URL before appending /commit/<sha>.

-- 1. Top-level commit URL
UPDATE artifact_versions
SET commit_body = jsonb_set(
    commit_body,
    '{url}',
    to_jsonb(
        regexp_replace(
            rtrim(commit_body->>'repository', '/'),
            '\.git$', ''
        )
        || '/commit/' || (commit_body->>'sha')
    )
)
WHERE commit_body->>'enrichedAt' IS NOT NULL
  AND commit_body->>'repository' IS NOT NULL
  AND commit_body->>'url' IS NULL;

-- 2. Submodule commit URLs
UPDATE artifact_versions
SET commit_body = jsonb_set(
    commit_body,
    '{submodules}',
    (
        SELECT jsonb_agg(
            CASE
                WHEN elem->>'repository' IS NOT NULL AND elem->>'url' IS NULL
                THEN elem || jsonb_build_object(
                    'url',
                    regexp_replace(
                        rtrim(elem->>'repository', '/'),
                        '\.git$', ''
                    )
                    || '/commit/' || (elem->>'sha')
                )
                ELSE elem
            END
        )
        FROM jsonb_array_elements(commit_body->'submodules') AS elem
    )
)
WHERE commit_body->'submodules' IS NOT NULL
  AND jsonb_array_length(commit_body->'submodules') > 0;
