package org.spongepowered.synchronizer.versionsync;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

import java.time.Duration;

public final class BatchVersionSyncManager {

    static final ServiceKey<Command> KEY = ServiceKey.create(Command.class, "batch-version-sync");

    interface Command extends Jsonable {
    }

    public record ArtifactToSync(ArtifactCoordinates coordinates) implements Command {
    }

    public enum Refresh implements Command {
        INSTANCE
    }

    public static Behavior<Command> setup() {
        return Behaviors.setup(ctx -> {
            ctx.getSystem().receptionist().tell(Receptionist.register(KEY, ctx.getSelf()));
            return timedSync(HashSet.empty());
        });
    }

    private static Behavior<Command> timedSync(Set<ArtifactCoordinates> coordinates) {
        return Behaviors.setup(ctx -> Behaviors.withTimers(timers -> {
            timers.startSingleTimer("refresh", Refresh.INSTANCE, Duration.ofSeconds(20));
            return Behaviors.receive(Command.class)
                .onMessage(ArtifactToSync.class, msg -> {
                    ctx.getLog().info("Received artifact to sync: {}", msg.coordinates);
                    if (coordinates.contains(msg.coordinates)) {
                        return Behaviors.same();
                    }
                    final var sharding = ClusterSharding.get(ctx.getSystem());
                    sharding.entityRefFor(ArtifactVersionSyncEntity.ENTITY_TYPE_KEY, msg.coordinates.asMavenString())
                        .tell(SyncRegistration.Refresh.INSTANCE);
                    return timedSync(coordinates.add(msg.coordinates));
                })
                .onMessage(Refresh.class, msg -> {
                    if (ctx.getLog().isDebugEnabled()) {
                        ctx.getLog().debug("Refreshing all artifacts: {}", coordinates);
                    }
                    final var sharding = ClusterSharding.get(ctx.getSystem());
                    coordinates.forEach(
                        c -> sharding.entityRefFor(ArtifactVersionSyncEntity.ENTITY_TYPE_KEY, c.asMavenString()).tell(
                            SyncRegistration.Refresh.INSTANCE)
                    );
                    timers.startSingleTimer("refresh", Refresh.INSTANCE, Duration.ofMinutes(1));
                    return Behaviors.same();
                })
                .build();
        }));
    }
}
