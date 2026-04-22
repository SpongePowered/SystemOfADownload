CREATE INDEX idx_versioned_tags_av_key_value
    ON artifact_versioned_tags (artifact_version_id, tag_key, tag_value);
