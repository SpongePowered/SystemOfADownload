package org.spongepowered.downloads.artifact.event;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.Artifact;

public interface ChangelogEvent extends Jsonable, AggregateEvent<ChangelogEvent> {

    AggregateEventTag<ChangelogEvent> INSTANCE = AggregateEventTag.of(ChangelogEvent.class);

    @Override
    default AggregateEventTagger<ChangelogEvent> aggregateTag() {
        return INSTANCE;
    }

    final record ChangelogCreated(Artifact artifact) implements ChangelogEvent {}

    final record ArtifactRegistered(Artifact artifact) implements ChangelogEvent { }
}
