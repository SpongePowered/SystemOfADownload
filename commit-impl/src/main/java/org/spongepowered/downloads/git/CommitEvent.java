package org.spongepowered.downloads.git;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.git.api.Commit;

public interface CommitEvent extends Jsonable, AggregateEvent<CommitEvent> {

    AggregateEventTag<CommitEvent> INSTANCE = AggregateEventTag.of(CommitEvent.class);

    @Override
    default AggregateEventTagger<CommitEvent> aggregateTag() {
        return INSTANCE;
    }

    @JsonDeserialize
    final class CommitCreated implements CommitEvent {
        public final Commit commit;

        public CommitCreated(final Commit commit) {
            this.commit = commit;
        }
    }

}
