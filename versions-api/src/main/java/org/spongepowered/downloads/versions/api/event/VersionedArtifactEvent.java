package org.spongepowered.downloads.versions.api.event;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;

import java.util.Objects;
import java.util.StringJoiner;

public interface VersionedArtifactEvent extends AggregateEvent<VersionedArtifactEvent>, Jsonable {

    AggregateEventShards<VersionedArtifactEvent> TAG = AggregateEventTag.sharded(VersionedArtifactEvent.class, 10);

    @Override
    default AggregateEventTagger<VersionedArtifactEvent> aggregateTag() {
        return TAG;
    }

    String asMavenCoordinates();

    class VersionRegistered implements VersionedArtifactEvent {

        public final MavenCoordinates coordinates;

        public VersionRegistered(final MavenCoordinates coordinates) {
            this.coordinates = coordinates;
        }



        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            VersionRegistered that = (VersionRegistered) o;
            return Objects.equals(coordinates, that.coordinates);
        }

        @Override
        public int hashCode() {
            return Objects.hash(coordinates);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", VersionRegistered.class.getSimpleName() + "[", "]")
                .add("coordinates=" + coordinates)
                .toString();
        }

        @Override
        public String asMavenCoordinates() {
            return this.coordinates.asStandardCoordinates();
        }
    }
}
