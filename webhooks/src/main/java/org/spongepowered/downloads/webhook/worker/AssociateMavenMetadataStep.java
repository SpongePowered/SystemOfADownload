package org.spongepowered.downloads.webhook.worker;

import akka.Done;
import io.vavr.control.Try;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.webhook.ArtifactProcessorEntity;
import org.spongepowered.downloads.webhook.sonatype.SonatypeClient;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final record AssociateMavenMetadataStep() implements WorkerStep<ArtifactProcessorEntity.Event.AssociatedMavenMetadata> {

    private static final Marker MARKER = MarkerManager.getMarker("AssociateMetadata");
    @Override
    public Try<Done> processEvent(
        final SonatypeArtifactWorkerService service,
        final ArtifactProcessorEntity.Event.AssociatedMavenMetadata event
    ) {
        final SonatypeClient client = SonatypeClient.configureClient().apply();
        final Artifact base = event.collection().getArtifactComponents().get("base")
            .getOrElse(() -> event.collection().getArtifactComponents().head()._2);
        return Try.of(() ->client.generateArtifactFrom(base)
            .map(sha -> service.webhookService.getProcessingEntity(event.mavenCoordinates())
                .ask(new ArtifactProcessorEntity.Command.AssociateCommitShaWithArtifact(event.collection(), sha))
            )
            .map(notUsed -> notUsed.thenApply(notUsed1 -> Done.done()))
            .toEither()
            .mapLeft(throwable -> {
                SonatypeArtifactWorkerService.LOGGER.log(Level.WARN, MARKER, throwable.getMessage());
                return CompletableFuture.completedFuture(Done.done());
            })
            .fold(Function.identity(), Function.identity())
            .toCompletableFuture()
            .join()
        );
    }

    @Override
    public Marker marker() {
        return MARKER;
    }
}
