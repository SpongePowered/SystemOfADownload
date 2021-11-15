package org.spongepowered.downloads.versions.worker.actor.delegates;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import org.spongepowered.downloads.versions.worker.actor.artifacts.CommitExtractor;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.VersionedArtifactCommand;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.VersionedArtifactEntity;

public final class RawCommitReceiver {

    public static Behavior<CommitExtractor.AssetCommitResponse> receive() {
        return Behaviors.setup(ctx -> {
            final var sharding = ClusterSharding.get(ctx.getSystem());
            return Behaviors.receive(CommitExtractor.AssetCommitResponse.class)
                .onMessage(CommitExtractor.FailedToRetrieveCommit.class, msg -> {
                    sharding.entityRefFor(VersionedArtifactEntity.ENTITY_TYPE_KEY, msg.asset().mavenCoordinates().asStandardCoordinates())
                        .tell(new VersionedArtifactCommand.MarkFilesAsErrored());
                    return Behaviors.same();
                })
                .onMessage(CommitExtractor.DiscoveredCommitFromFile.class, msg -> {
                    sharding.entityRefFor(VersionedArtifactEntity.ENTITY_TYPE_KEY, msg.asset().mavenCoordinates().asStandardCoordinates())
                        .tell(new VersionedArtifactCommand.RegisterRawCommit(msg.sha()));
                    return Behaviors.same();
                })
                .build();
        });
    }
}
