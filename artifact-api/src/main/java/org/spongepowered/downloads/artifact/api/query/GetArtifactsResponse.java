package org.spongepowered.downloads.artifact.api.query;

import io.vavr.collection.List;

public sealed interface GetArtifactsResponse {

    final record GroupMissing(
        String groupRequested
    ) implements GetArtifactsResponse {
    }

    final record ArtifactsAvailable(
        List<String> artifactIds
    ) implements GetArtifactsResponse {
    }
}
