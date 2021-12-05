package org.spongepowered.downloads.versions.worker.actor.delegates;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.spongepowered.downloads.versions.api.delegates.CommitDetailsRegistrar;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.VersionedArtifactCommand;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.VersionedArtifactEntity;

import java.time.Duration;

public class InternalCommitRegistrar {

    public static Behavior<CommitDetailsRegistrar.Command> register() {
        return Behaviors.setup(ctx -> {
            final var sharding = ClusterSharding.get(ctx.getSystem());
            return Behaviors.receive(CommitDetailsRegistrar.Command.class)
                .onMessage(CommitDetailsRegistrar.HandleVersionedCommitReport.class, msg -> {
                    final var future = sharding
                        .entityRefFor(VersionedArtifactEntity.ENTITY_TYPE_KEY, msg.coordinates().asStandardCoordinates())
                        .<Done>ask(replyTo -> new VersionedArtifactCommand.RegisterResolvedCommit(msg.versionedCommit(), msg.repo(), replyTo),
                            Duration.ofMinutes(20)
                        )
                        .toCompletableFuture();
                    ctx.pipeToSelf(future, (done, failure) -> {
                        if (failure != null) {
                            ctx.getLog().warn("Failed registering git details", failure);
                        }
                        return new CompletedWork(msg.replyTo());
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

    @JsonTypeName("completed-work")
    static final record CompletedWork(ActorRef<Done> replyTo) implements CommitDetailsRegistrar.Command {
        @JsonCreator
        public CompletedWork {
        }
    }

}
