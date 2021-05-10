package org.spongepowered.downloads.versions.sonatype.resync;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.maven.artifact.ArtifactMavenMetadata;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

interface SynchronizeEvent extends Jsonable, AggregateEvent<SynchronizeEvent> {

    AggregateEventShards<SynchronizeEvent> TAG = AggregateEventTag.sharded(SynchronizeEvent.class, 10);

    @Override
    default AggregateEventTagger<SynchronizeEvent> aggregateTag() {
        return TAG;
    }

    @JsonDeserialize
    final class SynchronizedArtifacts implements SynchronizeEvent {

        public final ArtifactMavenMetadata metadata;
        public final String updatedTime;

        @JsonCreator
        public SynchronizedArtifacts(final ArtifactMavenMetadata metadata, final String updatedTime) {
            this.metadata = Objects.requireNonNull(metadata, "metadata");
            this.updatedTime = Objects.requireNonNull(updatedTime, "updatedTime");
        }
    }
}
