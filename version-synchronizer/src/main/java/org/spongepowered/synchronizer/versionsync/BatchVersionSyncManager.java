/*
 * This file is part of SystemOfADownload, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://spongepowered.org/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
