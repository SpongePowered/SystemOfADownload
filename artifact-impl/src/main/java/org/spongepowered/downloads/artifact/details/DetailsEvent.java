package org.spongepowered.downloads.artifact.details;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

public interface DetailsEvent extends AggregateEvent<DetailsEvent>, Jsonable {

    AggregateEventShards<DetailsEvent> TAG = AggregateEventTag.sharded(DetailsEvent.class, 10);

    @Override
    default AggregateEventTagger<DetailsEvent> aggregateTag() {
        return TAG;
    }

    @JsonDeserialize
    final class ArtifactRegistered implements DetailsEvent {
        public final ArtifactCoordinates coordinates;

        @JsonCreator
        public ArtifactRegistered(final ArtifactCoordinates coordinates) {
            this.coordinates = coordinates;
        }
    }
}
