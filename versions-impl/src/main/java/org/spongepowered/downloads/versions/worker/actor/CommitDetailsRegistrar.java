package org.spongepowered.downloads.versions.worker.actor;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.VersionedCommit;
import org.spongepowered.downloads.versions.worker.domain.GitBasedArtifact;
import org.spongepowered.downloads.versions.worker.domain.GitCommand;

import java.time.Duration;

public final class CommitDetailsRegistrar {

    public interface Command {}

    public static final record HandleVersionedCommitReport(
        VersionedCommit versionedCommit,
        MavenCoordinates coordinates,
        ActorRef<Done> replyTo
    ) implements Command {}

    private static final record CompletedWork(ActorRef<Done> replyTo) implements Command {}

    public static Behavior<Command> register() {
        return Behaviors.setup(ctx -> {
            final var sharding = ClusterSharding.get(ctx.getSystem());
            return Behaviors.receive(Command.class)
                .onMessage(HandleVersionedCommitReport.class, msg -> {
                    final var future = sharding
                        .entityRefFor(GitBasedArtifact.ENTITY_TYPE_KEY, msg.coordinates.asArtifactCoordinates().asMavenString())
                        .<Done>ask(replyTo -> new GitCommand.AssociateCommitDetailsForVersion(msg.coordinates, msg.versionedCommit, replyTo),
                            Duration.ofSeconds(20)
                        )
                        .toCompletableFuture();
                    ctx.pipeToSelf(future, (done, failure) -> {
                        if (failure != null) {
                            ctx.getLog().warn("Failed registering git details", failure);
                        }
                        return new CompletedWork(msg.replyTo);
                    });
                    return Behaviors.same();
                })
                .onMessage(CompletedWork.class, msg -> {
                    msg.replyTo.tell(Done.getInstance());
                    return Behaviors.same();
                })
                .build();
        });
    }
}
