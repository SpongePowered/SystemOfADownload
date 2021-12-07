package org.spongepowered.synchronizer.versionsync;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;


@JsonDeserialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(VersionSyncEvent.RegisteredVersion.class),
    @JsonSubTypes.Type(VersionSyncEvent.RegisteredBatch.class),
    @JsonSubTypes.Type(VersionSyncEvent.StartedBatchRegistration.class),
    @JsonSubTypes.Type(VersionSyncEvent.ResolvedVersion.class),
    @JsonSubTypes.Type(VersionSyncEvent.FailedVersion.class)
})
public sealed interface VersionSyncEvent extends AggregateEvent<VersionSyncEvent>, Jsonable {

    AggregateEventShards<VersionSyncEvent> INSTANCE = AggregateEventTag.sharded(VersionSyncEvent.class, 10);

    @Override
    default AggregateEventTagger<VersionSyncEvent> aggregateTag() {
        return INSTANCE;
    }

    @JsonTypeName("registered-version")
    record RegisteredVersion(MavenCoordinates coordinates) implements VersionSyncEvent {}

    @JsonTypeName("registered-batch")
    record RegisteredBatch(ArtifactCoordinates artifact, List<MavenCoordinates> coordinates) implements VersionSyncEvent {
    }

    @JsonTypeName("started-batch")
    record StartedBatchRegistration(List<MavenCoordinates> batched) implements VersionSyncEvent {
    }

    @JsonTypeName("resolved-version")
    record ResolvedVersion(MavenCoordinates coordinates) implements VersionSyncEvent {
    }

    @JsonTypeName("failed-version")
    record FailedVersion(MavenCoordinates coordinates) implements VersionSyncEvent {
    }
}
