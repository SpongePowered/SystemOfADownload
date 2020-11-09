package org.spongepowered.downloads.webhook.worker;

import akka.Done;
import akka.stream.javadsl.Flow;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import io.vavr.control.Try;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.changelog.api.ChangelogService;
import org.spongepowered.downloads.webhook.ArtifactProcessorEntity;
import org.spongepowered.downloads.webhook.SonatypeWebhookService;

import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.function.Function;

public class SonatypeArtifactWorkerService implements Service {

    static final Logger LOGGER = LogManager.getLogger(SonatypeArtifactWorkerService.class);
    static final Marker SERVICE = MarkerManager.getMarker("Service");

    private static final Function<Class<? extends ArtifactProcessorEntity.Event>, Optional<WorkerStep<?>>> WORKER_STAGES;

    final ArtifactService artifacts;
    final ChangelogService changelog;
    final SonatypeWebhookService webhookService;

    @Inject
    public SonatypeArtifactWorkerService(
        final ArtifactService artifacts, final ChangelogService changelog,
        final SonatypeWebhookService webhookService
    ) {
        this.artifacts = artifacts;
        this.changelog = changelog;
        webhookService.topic().subscribe()
            .atLeastOnce(Flow.<ArtifactProcessorEntity.Event>create().map(this::processEvent));
        this.webhookService = webhookService;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Done processEvent(final ArtifactProcessorEntity.Event event) {
       return SonatypeArtifactWorkerService.WORKER_STAGES.apply(event.getClass())
            .map(step -> {
                LOGGER.log(
                    Level.DEBUG,
                    step.marker(),
                    "Starting process of event %s[%s]",
                    () -> event.getClass().getSimpleName(),
                    event::toString
                );
                return (Try<Done>) ((WorkerStep) step).processEvent(this, event);
            })
            .orElseGet(() -> {
                LOGGER.log(Level.WARN, SERVICE, String.format("Could not find a worker step for %s event", event.getClass()));
                return Try.success(Done.done());
            })
           .toEither()
           .mapLeft(exception -> {
               LOGGER.error(SERVICE, "Failure in processing event", exception);
               return Done.done();
           })
           .fold(Function.identity(), Function.identity());
    }

    @Override
    public Descriptor descriptor() {
        return Service.named("artifact-worker")
            ;
    }

    static {
        final IdentityHashMap<Class<? extends ArtifactProcessorEntity.Event>, WorkerStep<?>> steps = new IdentityHashMap<>();
        steps.put(ArtifactProcessorEntity.Event.InitializeArtifactForProcessing.class, new RegisterArtifactsStep());
        steps.put(ArtifactProcessorEntity.Event.AssociateCommitSha.class, new FetchPriorBuildStep());
        steps.put(ArtifactProcessorEntity.Event.AssociatedMavenMetadata.class, new AssociateMavenMetadataStep());
        WORKER_STAGES = (eventClass) -> Optional.ofNullable(steps.get(eventClass));
    }
}
