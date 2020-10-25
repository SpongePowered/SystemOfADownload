package org.spongepowered.downloads.artifact.api.query;

import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.Group;

public sealed interface GetArtifactsResponse
    permits GetArtifactsResponse.ArtifactMissing,
        GetArtifactsResponse.GroupMissing,
        GetArtifactsResponse.ArtifactsAvailable {

    final record GroupMissing(
        String groupRequested
    ) implements GetArtifactsResponse {
    }

    final record ArtifactMissing(
        String requested,
        Group targeted
    ) implements GetArtifactsResponse {
    }

    final record ArtifactsAvailable(
        List<Artifact> artifacts
    ) implements GetArtifactsResponse {
    }
}
