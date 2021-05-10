package org.spongepowered.downloads.maven.snapshot;

public final class SnapshotMetadata {
    public final String groupId;
    public final String artifactId;
    public final String version;
    public final SnapshotVersioning versioning;

    public SnapshotMetadata(
        final String groupId, final String artifactId, final String version,
        final SnapshotVersioning versioning
    ) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.versioning = versioning;
    }
}
