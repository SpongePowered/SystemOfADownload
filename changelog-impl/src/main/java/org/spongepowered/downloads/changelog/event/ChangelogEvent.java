package org.spongepowered.downloads.changelog.event;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.Artifact;

import java.io.Serial;
import java.util.Objects;

public interface ChangelogEvent extends Jsonable, AggregateEvent<ChangelogEvent> {

    AggregateEventTag<ChangelogEvent> INSTANCE = AggregateEventTag.of(ChangelogEvent.class);

    @Override
    default AggregateEventTagger<ChangelogEvent> aggregateTag() {
        return INSTANCE;
    }

    final static class ChangelogCreated implements ChangelogEvent {
        @Serial private static final long serialVersionUID = 0L;
        private final Artifact artifact;

        public ChangelogCreated(Artifact artifact) {
            this.artifact = artifact;
        }

        public Artifact artifact() {
            return this.artifact;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (ChangelogCreated) obj;
            return Objects.equals(this.artifact, that.artifact);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.artifact);
        }

        @Override
        public String toString() {
            return "ChangelogCreated[" +
                "artifact=" + this.artifact + ']';
        }
    }

    final static class ArtifactRegistered implements ChangelogEvent {
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
