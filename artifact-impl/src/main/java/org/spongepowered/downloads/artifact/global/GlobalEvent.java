package org.spongepowered.downloads.artifact.global;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;

public interface GlobalEvent extends AggregateEvent<GlobalEvent>, Jsonable {

    AggregateEventTag<GlobalEvent> TAG = AggregateEventTag.of(GlobalEvent.class);

    @Override
    default AggregateEventTagger<GlobalEvent> aggregateTag() {
        return TAG;
    }
}
