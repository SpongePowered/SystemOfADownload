package org.spongepowered.downloads.changelog.api.query;

import org.spongepowered.downloads.changelog.api.Changelog;

public sealed interface ChangelogResponse {

    final record ArtifactMissing(String artifactId, String groupId, String version) implements ChangelogResponse {}

    final record GroupMissing(String groupId) implements ChangelogResponse {}

    final record VersionMissing(String version, String artifactId, String groupId) implements ChangelogResponse {}

    final record VersionedChangelog(Changelog changeLog) implements ChangelogResponse {}
}
