package org.spongepowered.downloads.artifact;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import org.spongepowered.downloads.artifact.api.registration.GetArtifactsResponse;

public sealed interface ArtifactCommand {

    final record GetArtifact(String groupId) implements ArtifactCommand, PersistentEntity.ReplyType<GetArtifactsResponse> {}
}
