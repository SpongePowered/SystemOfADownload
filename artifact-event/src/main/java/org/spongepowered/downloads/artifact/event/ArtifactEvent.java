package org.spongepowered.downloads.artifact.event;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.Artifact;

import java.io.Serial;
import java.util.Objects;

public interface ArtifactEvent extends Jsonable, AggregateEvent<ArtifactEvent> {

    AggregateEventShards<ArtifactEvent> INSTANCE = AggregateEventTag.sharded(ArtifactEvent.class, 10);

    @Override
    default AggregateEventTagger<ArtifactEvent> aggregateTag() {
        return INSTANCE;
    }

    final static class ArtifactRegistered implements ArtifactEvent {
        @Serial private static final long serialVersionUID = 0L;
        private final Artifact artifact;

        public ArtifactRegistered(Artifact artifact) {
            this.artifact = artifact;
        }

        public Artifact artifact() {
            return this.artifact;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (ArtifactRegistered) obj;
            return Objects.equals(this.artifact, that.artifact);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.artifact);
        }

        @Override
        public String toString() {
            return "ArtifactRegistered[" +
                "artifact=" + this.artifact + ']';
        }
    }
}
