package org.spongepowered.downloads.versions.worker.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.versions.server.collection.ACCommand;
import org.spongepowered.downloads.versions.server.collection.VersionedArtifactAggregate;

import java.time.Duration;

public class AssetRetriever {

    private static final record InternalResponse(
        List<ArtifactCollection> collection,
        ActorRef<List<ArtifactCollection>> replyTo
    ) implements ACCommand {
    }
    public static Behavior<ACCommand> retrieveAssetCollection() {
        return Behaviors.setup(ctx -> {
            final var sharding = ClusterSharding.get(ctx.getSystem());
            return Behaviors.receive(ACCommand.class)
                .onMessage(ACCommand.GetCollections.class, msg -> {
                    final var coordinates = msg.coordinates().head().asArtifactCoordinates();

                    ctx.pipeToSelf(sharding.entityRefFor(
                            VersionedArtifactAggregate.ENTITY_TYPE_KEY,
                            coordinates.asMavenString()
                        )
                        .<List<ArtifactCollection>>ask(
                            replyTo ->
                                new ACCommand.GetCollections(msg.coordinates(), replyTo),
                            Duration.ofSeconds(20)
                        ), (result, throwable)-> {
                        if (throwable != null) {
                            return new InternalResponse(List.empty(), msg.replyTo());
                        }
                        return new InternalResponse(result, msg.replyTo());
                    });
                    return Behaviors.same();
                })
                .onMessage(InternalResponse.class, msg -> {
                    msg.replyTo.tell(msg.collection);
                    return Behaviors.same();
                })
                .build();
        });
    }
}
