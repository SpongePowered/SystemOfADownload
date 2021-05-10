package org.spongepowered.downloads.artifact.details;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.group.GroupEvent;

public interface DetailsEvent extends AggregateEvent<DetailsEvent>, Jsonable {

    AggregateEventShards<DetailsEvent> TAG = AggregateEventTag.sharded(DetailsEvent.class, 10);

    @Override
    default AggregateEventTagger<DetailsEvent> aggregateTag() {
        return TAG;
    }
}
