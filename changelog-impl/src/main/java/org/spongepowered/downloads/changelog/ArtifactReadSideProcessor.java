package org.spongepowered.downloads.changelog;

import akka.Done;
import akka.japi.Pair;
import akka.stream.Attributes;
import akka.stream.javadsl.Flow;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.Offset;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import org.pcollections.PSequence;
import org.spongepowered.downloads.artifact.event.ArtifactEvent;
import org.spongepowered.downloads.changelog.api.ChangelogService;

import java.util.concurrent.CompletableFuture;

public class ArtifactReadSideProcessor extends ReadSideProcessor<ArtifactEvent> {

    final ChangelogService changelogService;

    @Inject
    public ArtifactReadSideProcessor(final ChangelogService service) {
        this.changelogService = service;
    }

    @Override
    public ReadSideHandler<ArtifactEvent> buildHandler() {
        return new ReadSideHandler<>() {
            @Override
            public Flow<Pair<ArtifactEvent, Offset>, Done, ?> handle() {
                return Flow.<Pair<ArtifactEvent, Offset>>create()
                    .log("ArtifactEvent")
                    .withAttributes(Attributes.createLogLevels(Attributes.logLevelInfo()))
                    .mapAsyncUnordered(10, e -> {
                        final ArtifactEvent event = e.first();
                        if (event instanceof ArtifactEvent.ArtifactRegistered) {
                            final var registered = (ArtifactEvent.ArtifactRegistered) event;
                            return ArtifactReadSideProcessor.this.changelogService
                                .registerArtifact(registered.artifact()).invoke()
                                .thenApply(none -> Done.done());
                        }
                        return CompletableFuture.completedFuture(Done.done());
                    });
            }
        };
    }

    @Override
    public PSequence<AggregateEventTag<ArtifactEvent>> aggregateTags() {
        return ArtifactEvent.INSTANCE.allTags();
    }
}
