package org.spongepowered.downloads.webhook;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;

import java.util.UUID;

public sealed interface ArtifactProcessingEvent extends AggregateEvent<ArtifactProcessingEvent>, Jsonable {

    AggregateEventTag<ArtifactProcessingEvent> TAG = AggregateEventTag.of(ArtifactProcessingEvent.class);

    @Override
    default AggregateEventTagger<ArtifactProcessingEvent> aggregateTag() {
        return TAG;
    }

    String mavenCoordinates();

    final record InitializeArtifactForProcessing(
        ArtifactProcessingState.State newState,
        String mavenCoordinates,
        String componentId) implements ArtifactProcessingEvent {
    }
}
