package org.spongepowered.downloads.artifact;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistrationResponse;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;

public sealed interface ArtifactCommand {

    final record GetArtifacts(String groupId) implements ArtifactCommand, PersistentEntity.ReplyType<GetArtifactsResponse> {}

    final record RegisterArtifactCommand(String groupId, String artifactId, String version)
        implements ArtifactCommand, PersistentEntity.ReplyType<ArtifactRegistrationResponse> {}
}
