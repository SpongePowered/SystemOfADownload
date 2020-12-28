package org.spongepowered.downloads.webhook.sonatype;

import io.vavr.collection.List;

import java.util.Optional;

public final record MavenMetadata(String groupId, Versioning versioning) {

    static final record Versioning(Snapshot snapshot, Optional<String> latest, Optional<String> release, String lastUpdated, List<String> versions, VersionMap snapshotVersions) {}

    static final record Snapshot(String timestamp, Integer buildNumber) {}

    static final record VersionMap(List<VersionedAsset> assets) {}

    static final record VersionedAsset(String classifier, String extension, String value, String updated) {}
}
