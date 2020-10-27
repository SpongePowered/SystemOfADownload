package org.spongepowered.downloads.artifact.api.query;

import io.vavr.collection.Map;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;

public sealed interface GetVersionsResponse {

    final record VersionsAvailable(Map<String, ArtifactCollection> artifacts) implements GetVersionsResponse {}

    final record GroupUnknown(String groupId) implements GetVersionsResponse {}

    final record ArtifactUnknown(String artifactId) implements GetVersionsResponse {}
}
