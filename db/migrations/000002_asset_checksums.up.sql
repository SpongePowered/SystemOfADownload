ALTER TABLE artifact_versioned_assets
    ADD COLUMN md5 TEXT,
    ADD COLUMN sha1 TEXT,
    ADD COLUMN sha512 TEXT,
    ADD COLUMN extension TEXT;
