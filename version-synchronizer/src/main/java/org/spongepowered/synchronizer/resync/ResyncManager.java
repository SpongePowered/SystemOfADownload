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
package org.spongepowered.synchronizer.resync;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.Behavior;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Routers;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorFlow;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.artifact.api.query.GroupsResponse;
import org.spongepowered.synchronizer.SynchronizerSettings;
import org.spongepowered.synchronizer.actor.ArtifactSyncWorker;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class ResyncManager {

    sealed interface Resync {
    }

    record PerformResync() implements Resync {
    }

    record ArtifactsToSync(List<ArtifactCoordinates> artifacts) implements Resync {
    }

    public static Behavior<Resync> create(
        final ArtifactService artifactService,
        final SynchronizerSettings.VersionSync versionSync
    ) {
        return Behaviors.withTimers(t -> {
            t.startTimerWithFixedDelay("resync", new PerformResync(), versionSync.interval);
            t.startSingleTimer("start", new PerformResync(), versionSync.startupDelay);
            return setup(artifactService);
        });
    }

    private static Behavior<Resync> setup(
        final ArtifactService artifactService
    ) {
        return Behaviors.setup(ctx -> {
            final var sharding = ClusterSharding.get(ctx.getSystem());
            final var dispatch = DispatcherSelector.defaultDispatcher();
            final var requester = ctx.spawn(
                Behaviors.supervise(RequestArtifactsToSync.create(artifactService)).onFailure(
                    SupervisorStrategy.restart()),
                String.format("requester-%s-%d", UUID.randomUUID(), System.currentTimeMillis()),
                dispatch
            );
            final var artifactSyncPool = Routers.pool(
                4,
                Behaviors.supervise(ArtifactSyncWorker.create(sharding))
                    .onFailure(
                        SupervisorStrategy.restartWithBackoff(Duration.ofSeconds(1), Duration.ofMinutes(10), 0.2))
            );
            final var syncWorker = ctx.spawn(
                artifactSyncPool,
                String.format("pooled-artifact-synchronization-workers-%s-%d", UUID.randomUUID(), System.currentTimeMillis()),
                dispatch
            );
            final var requestFlow = ActorFlow.ask(
                requester,
                Duration.ofSeconds(30),
                RequestArtifactsToSync.GatherGroupArtifacts::new
            );

            final var registrationFlow = ActorFlow.ask(
                syncWorker,
                Duration.ofMinutes(20),
                ArtifactSyncWorker.PerformResync::new
            );
            return awaiting(artifactService, requestFlow, registrationFlow);
        });
    }

    private static Behavior<Resync> awaiting(
        final ArtifactService artifactService,
        final Flow<String, RequestArtifactsToSync.ArtifactsToSync, NotUsed> requestFlow,
        final Flow<ArtifactCoordinates, Done, NotUsed> registrationFlow
    ) {
        return Behaviors.setup(ctx -> Behaviors.receive(Resync.class)
            .onMessage(ArtifactsToSync.class, result -> {
                Source.from(result.artifacts)
                    .async()
                    .via(registrationFlow.async())
                    .runWith(Sink.ignore(), ctx.getSystem());
                return Behaviors.same();
            })
            .onMessage(PerformResync.class, g -> {
                final var makeRequest = artifactService.getGroups()
                    .invoke()
                    .thenApply(groups -> ((GroupsResponse.Available) groups).groups)
                    .thenCompose(groups -> {
                        final Sink<List<ArtifactCoordinates>, CompletionStage<List<ArtifactCoordinates>>> fold = Sink.fold(
                            List.empty(), List::appendAll);
                        return Source.from(groups.map(Group::getGroupCoordinates).asJava())
                            .async()
                            .via(requestFlow)
                            .map(RequestArtifactsToSync.ArtifactsToSync::artifactsNeeded)
                            .runWith(fold, ctx.getSystem());
                    });
                ctx.pipeToSelf(makeRequest, (ok, exception) -> {
                    if (exception != null) {
                        ctx.getLog().error("Failed to process sync", exception);
                    }
                    return new ArtifactsToSync(ok);
                });
                return Behaviors.same();
            })
            .build());

    }
}