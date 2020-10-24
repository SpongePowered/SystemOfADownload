package org.spongepowered.downloads.artifact.event;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;

public interface ArtifactEvent extends Jsonable, AggregateEvent<ArtifactEvent> {

    AggregateEventTag<ArtifactEvent> INSTANCE = AggregateEventTag.of(ArtifactEvent.class);

    @Override
    default AggregateEventTagger<ArtifactEvent> aggregateTag() {
        return INSTANCE;
    }
}
