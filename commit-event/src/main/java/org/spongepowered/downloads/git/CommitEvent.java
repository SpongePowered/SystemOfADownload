package org.spongepowered.downloads.git;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.git.api.Commit;
import org.spongepowered.downloads.git.api.Repository;

public sealed interface CommitEvent extends Jsonable, AggregateEvent<CommitEvent>
    permits CommitEvent.CommitCreated, CommitEvent.GitRepoRegistered {

    AggregateEventTag<CommitEvent> INSTANCE = AggregateEventTag.of(CommitEvent.class);

    @Override
    default AggregateEventTagger<CommitEvent> aggregateTag() {
        return INSTANCE;
    }

    @JsonDeserialize
    final record CommitCreated(Commit commit) implements CommitEvent {
    }

    @JsonDeserialize
    final record GitRepoRegistered(Repository repository) implements CommitEvent {
    }

}
