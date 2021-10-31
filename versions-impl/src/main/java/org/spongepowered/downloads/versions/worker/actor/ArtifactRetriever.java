package org.spongepowered.downloads.versions.worker.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.versions.worker.actor.global.GlobalCommand;
import org.spongepowered.downloads.versions.worker.actor.global.GlobalRegistration;

import java.time.Duration;

public final class ArtifactRetriever {

    private static final record Response(List<ArtifactCoordinates> artifacts, ActorRef<List<ArtifactCoordinates>> replyTo)
    implements GlobalCommand {
        @JsonCreator
        private Response {
        }
    }

    public static Behavior<GlobalCommand> getArtifacts() {
        return Behaviors.setup(ctx -> {
            final var sharding = ClusterSharding.get(ctx.getSystem());
            return Behaviors.receive(GlobalCommand.class)
                .onMessage(GlobalCommand.GetArtifacts.class, msg -> {
                    ctx.pipeToSelf(sharding.entityRefFor(GlobalRegistration.ENTITY_TYPE_KEY, "global")
                        .<List<ArtifactCoordinates>>ask(
                            GlobalCommand.GetArtifacts::new,
                            Duration.ofSeconds(10))
                        .toCompletableFuture(),
                        (result, throwable) -> {
                        if (throwable != null) {
                            ctx.getLog().warn("Failed to retrieve global artifacts", throwable);
                            return new Response(List.empty(), msg.replyTo());
                        }
                        return new Response(result, msg.replyTo());
                        });
                    return Behaviors.same();
                })
                .onMessage(Response.class, msg -> {
                    msg.replyTo.tell(msg.artifacts);
                    return Behaviors.same();
                })
                .build();
        });
    }
}
