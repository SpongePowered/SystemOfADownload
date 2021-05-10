package org.spongepowered.downloads.versions.sonatype.global;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

public interface GloballyManagedArtifactEvent extends Jsonable, AggregateEvent<GloballyManagedArtifactEvent> {
    AggregateEventShards<GloballyManagedArtifactEvent> INSTANCE = AggregateEventTag.sharded(GloballyManagedArtifactEvent.class, 10);

    @Override
    default AggregateEventTagger<GloballyManagedArtifactEvent> aggregateTag() {
        return INSTANCE;
    }

    static final class Registered implements GloballyManagedArtifactEvent {

        public final ArtifactCoordinates coordinates;

        public Registered(final ArtifactCoordinates coordinates) {
            this.coordinates = coordinates;
        }
    }
}
