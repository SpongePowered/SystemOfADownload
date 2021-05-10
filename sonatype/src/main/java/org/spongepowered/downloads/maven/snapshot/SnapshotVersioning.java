package org.spongepowered.downloads.maven.snapshot;

import io.vavr.collection.List;

/**
 * Represents a snapshot versioned maven metadata xml that SOAD will use to represent
 * possible artifacts of snapshots. Note that due to the implicit requirements of
 */
public class SnapshotVersioning {

    public final Snapshot snapshot;
    public final String lastUpdated;
    public final List<SnapshotAsset> snapshotVersions;

    public SnapshotVersioning(
        final Snapshot snapshot, final String lastUpdated,
        final List<SnapshotAsset> snapshotVersions
    ) {
        this.snapshot = snapshot;
        this.lastUpdated = lastUpdated;
        this.snapshotVersions = snapshotVersions;
    }
}
