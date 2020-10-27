package org.spongepowered.downloads.webhook;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;

public sealed interface ArtifactProcessingCommand {

    final record StartProcessing(
        SonatypeWebhookService.SonatypeData webhook,
        ArtifactCollection artifact
    ) implements ArtifactProcessingCommand, PersistentEntity.ReplyType<NotUsed> {
    }

    final record FetchJarAndPullMetadata(
        ArtifactCollection collection
    ) implements ArtifactProcessingCommand, PersistentEntity.ReplyType<NotUsed> {

    }

}
