package org.spongepowered.downloads.artifact.api.query;

import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.Artifact;

public sealed interface ArtifactRegistrationResponse
    extends Jsonable {

    final record ArtifactAlreadyRegistered(
        String artifactName,
        String groupId
    ) implements ArtifactRegistrationResponse {
    }

    final record RegisteredArtifact(
        Artifact artifact
    ) implements ArtifactRegistrationResponse {
    }

    final record GroupMissing(String s) implements ArtifactRegistrationResponse {
    }
}
