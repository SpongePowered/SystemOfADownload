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
package org.spongepowered.synchronizer.actor;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.synchronizer.resync.domain.ArtifactSynchronizerAggregate;
import org.spongepowered.synchronizer.versionsync.ArtifactVersionSyncEntity;
import org.spongepowered.synchronizer.versionsync.SyncRegistration;

import java.time.Duration;

public final class ArtifactSyncWorker {

    public interface Command {
    }

    /**
     * Starts the request that the specific artifact described by the
     * {@link ArtifactCoordinates coordinates} are synchronized, versioned,
     * and other side effects. A {@link Done} response is always given,
     * regardless of outcome, since the sync performs multiple jobs in the
     * background.
     */
    public record PerformResync(ArtifactCoordinates coordinates, ActorRef<Done> replyTo)
        implements Command {
    }

    /**
     * For use with just getting a {@link Done} reply back, some messages can be ignored.
     */
    public record Ignored(ActorRef<Done> replyTo) implements Command {
    }

    private record Failed(ArtifactCoordinates coordinates, ActorRef<Done> replyTo) implements Command {
    }

    /**
     * A post-reqeusted result signifying that the initial request for an
     * artifact's versions to sync has been completed.
     */
    private record WrappedResult(ActorRef<Done> replyTo) implements Command {
    }

    public static Behavior<Command> create(
        final ClusterSharding clusterSharding
    ) {
        return Behaviors.setup(ctx -> {
            final ArtifactSyncExtension.Settings settings = ArtifactSyncExtension.SettingsProvider.get(ctx.getSystem());
            return awaiting(clusterSharding, settings);
        });
    }

    private static Behavior<Command> awaiting(
        final ClusterSharding clusterSharding,
        final ArtifactSyncExtension.Settings settings
    ) {
        return Behaviors.setup(ctx -> Behaviors.receive(Command.class)
            .onMessage(PerformResync.class, msg -> {
                ctx.getLog().debug("Running Sync");
                final var globalResyncRef = clusterSharding.entityRefFor(
                    ArtifactSynchronizerAggregate.ENTITY_TYPE_KEY,
                    msg.coordinates.asMavenString()
                );
                final var versionSyncRef = clusterSharding.entityRefFor(
                    ArtifactVersionSyncEntity.ENTITY_TYPE_KEY,
                    msg.coordinates.asMavenString()
                );
                ctx.pipeToSelf(
                    globalResyncRef
                        .<List<MavenCoordinates>>ask(
                            replyTo -> new org.spongepowered.synchronizer.resync.domain.Command.Resync(msg.coordinates, replyTo), settings.individualTimeOut)
                        .thenCompose(response ->
                            versionSyncRef
                                .<Done>ask(
                                    replyTo -> new SyncRegistration.SyncBatch(msg.coordinates, response, replyTo),
                                    Duration.ofMinutes(10)
                                )
                        ),
                    (ok, exception) -> {
                        if (exception != null) {
                            ctx.getLog().error("Failed to resync by maven coordinates, may ask again", exception);
                            return new Failed(msg.coordinates, msg.replyTo);
                        }

                        return new WrappedResult(msg.replyTo);
                    }
                );
                return Behaviors.same();
            })
            .onMessage(WrappedResult.class, msg -> {
                msg.replyTo.tell(Done.done());
                return Behaviors.same();
            })
            .onMessage(Ignored.class, msg -> {
                msg.replyTo.tell(Done.done());
                return Behaviors.same();
            })
            .onMessage(Failed.class, msg -> {
                msg.replyTo.tell(Done.done());
                return Behaviors.same();
            })
            .build());
    }

}
