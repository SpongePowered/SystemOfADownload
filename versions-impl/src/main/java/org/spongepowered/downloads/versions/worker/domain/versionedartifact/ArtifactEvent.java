package org.spongepowered.downloads.versions.worker.domain.versionedartifact;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.VersionedCommit;

import java.net.URI;

@JsonDeserialize
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
public sealed interface ArtifactEvent extends AggregateEvent<ArtifactEvent>, Jsonable {

    AggregateEventShards<ArtifactEvent> INSTANCE = AggregateEventTag.sharded(ArtifactEvent.class, 100);

    @Override
    default AggregateEventTagger<ArtifactEvent> aggregateTag() {
        return INSTANCE;
    }

    final record Registered(MavenCoordinates coordinates) implements ArtifactEvent {}

    final record AssetsUpdated(List<Artifact> artifacts) implements ArtifactEvent {}

    final record FilesErrored() implements ArtifactEvent { }

    final record CommitAssociated(MavenCoordinates coordinates, List<String> repos, String commitSha) implements ArtifactEvent { }

    final record RepositoryRegistered(String repository) implements ArtifactEvent {
    }

    final record CommitResolved(
        MavenCoordinates coordinates,
        URI repo,
        VersionedCommit versionedCommit
    ) implements ArtifactEvent {
    }
}
