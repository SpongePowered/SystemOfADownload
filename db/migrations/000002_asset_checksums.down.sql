ALTER TABLE artifact_versioned_assets
    DROP COLUMN md5,
    DROP COLUMN sha1,
    DROP COLUMN sha512,
    DROP COLUMN extension;
