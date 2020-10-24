package org.spongepowered.downloads.artifact.api.registration;

import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.Artifact;

public sealed interface ArtifactRegistrationResponse
    extends Jsonable
    permits
        ArtifactRegistrationResponse.ArtifactAlreadyRegistered,
        ArtifactRegistrationResponse.RegisteredArtifact {

    final record ArtifactAlreadyRegistered(
        String artifactName,
        String groupId
    ) implements ArtifactRegistrationResponse {
    }

    final record RegisteredArtifact(
        Artifact artifact
    ) implements ArtifactRegistrationResponse {
    }
}
