package org.spongepowered.downloads.versions.worker.domain.versionedartifact;

import akka.Done;
import akka.actor.typed.ActorRef;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.VersionedCommit;

import java.net.URI;

@JsonDeserialize
public sealed interface VersionedArtifactCommand {

    final record Register(
        MavenCoordinates coordinates,
        ActorRef<Done> replyTo
    ) implements VersionedArtifactCommand {
    }

    final record AddAssets(
        List<Artifact> artifacts,
        ActorRef<Done> replyTo
    ) implements VersionedArtifactCommand {
    }

    final record MarkFilesAsErrored() implements VersionedArtifactCommand {
    }

    final record RegisterRawCommit(String commitSha) implements VersionedArtifactCommand {}

    final record RegisterRepo(
        MavenCoordinates second,
        String repository,
        ActorRef<Done> replyTo
    ) implements VersionedArtifactCommand {
    }

    final record RegisterResolvedCommit(
        VersionedCommit versionedCommit,
        URI repo,
        ActorRef<Done> replyTo
    )
        implements VersionedArtifactCommand {
    }
}
