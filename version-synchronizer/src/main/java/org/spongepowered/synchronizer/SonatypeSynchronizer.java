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
package org.spongepowered.synchronizer;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.internal.receptionist.ReceptionistMessages;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Routers;
import akka.actor.typed.receptionist.ServiceKey;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorFlow;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.artifact.api.event.GroupUpdate;
import org.spongepowered.downloads.artifact.api.query.GroupsResponse;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.api.models.ArtifactUpdate;
import org.spongepowered.synchronizer.actor.ArtifactSyncWorker;
import org.spongepowered.synchronizer.actor.RequestArtifactsToSync;
import org.spongepowered.synchronizer.actor.VersionedComponentWorker;
import org.spongepowered.synchronizer.resync.ArtifactSynchronizerAggregate;
import scala.Option;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

public final class SonatypeSynchronizer {

    private static final ServiceKey<Command> key = ServiceKey.create(Command.class, "Synchronizer");

    public interface Command {
    }

    private static final record GatherGroupArtifacts() implements SonatypeSynchronizer.Command {
    }

    private static final record WrappedArtifactsToSync(
        List<ArtifactCoordinates> artifactCoordinates) implements SonatypeSynchronizer.Command {
    }

    public static Behavior<SonatypeSynchronizer.Command> create(
        final ArtifactService artifactService,
        final VersionsService versionsService,
        final ClusterSharding clusterSharding,
        final ObjectMapper mapper
    ) {
        return Behaviors.setup(context -> {
            context.getLog().info("Initializing Artifact Maven Synchronization");
            context.getSystem().receptionist().tell(
                new ReceptionistMessages.Register<>(key, context.getSelf(), Option.empty()));
            final var settings = SynchronizationExtension.SettingsProvider.get(context.getSystem());
            clusterSharding
                .init(
                    Entity.of(
                        ArtifactSynchronizerAggregate.ENTITY_TYPE_KEY,
                        ArtifactSynchronizerAggregate::create
                    )
                );

            SonatypeSynchronizer.subscribeToArtifactUpdates(context, artifactService, versionsService, clusterSharding, settings);

            SonatypeSynchronizer.subscribeToVersionedArtifactUpdates(versionsService, mapper, context, settings);

            // Scheduled full resynchronization with maven and therefor sonatype
            return Behaviors.withTimers(timers -> {
                timers.startTimerWithFixedDelay(new GatherGroupArtifacts(), settings.versionSync.interval);
                timers.startSingleTimer(new GatherGroupArtifacts(), settings.versionSync.startupDelay);
                return timedSync(artifactService, versionsService, clusterSharding);
            });
        });
    }

    private static void subscribeToVersionedArtifactUpdates(
        final VersionsService versionsService, final ObjectMapper mapper, final ActorContext<Command> context,
        final SynchronizerSettings settings
    ) {
        // region Synchronize Versioned Assets through Sonatype Search
        final var componentPool = Routers.pool(
            settings.asset.poolSize,
            Behaviors.supervise(VersionedComponentWorker.gatherComponents(versionsService, mapper))
                .onFailure(settings.asset.backoff)
        );
        final var componentRef = context.spawn(
            componentPool,
            "version-component-registration",
            DispatcherSelector.defaultDispatcher()
        );
        final Flow<ArtifactUpdate, Done, NotUsed> versionedFlow = ActorFlow.ask(
            settings.asset.parallelism,
            componentRef,
            settings.asset.timeout,
            (g, b) -> {
                if (!(g instanceof ArtifactUpdate.ArtifactVersionRegistered a)) {
                    return new VersionedComponentWorker.Ignored(b);
                }
                return new VersionedComponentWorker.GatherComponentsForArtifact(a.coordinates(), b);
            }
        );
        versionsService.artifactUpdateTopic()
            .subscribe()
            .atLeastOnce(versionedFlow);
        // endregion
    }

    private static void subscribeToArtifactUpdates(
        final ActorContext<SonatypeSynchronizer.Command> context,
        final ArtifactService artifactService,
        final VersionsService versionsService,
        final ClusterSharding clusterSharding,
        final SynchronizerSettings settings
    ) {
        // region Synchronize Artifact Versions from Maven

        final var syncWorkerBehavior = ArtifactSyncWorker.create(versionsService, clusterSharding);
        final var pool = Routers.pool(
            settings.reactiveSync.poolSize,
            syncWorkerBehavior
        );

        final var registrationRef = context.spawn(
            pool,
            "group-event-subscriber",
            DispatcherSelector.defaultDispatcher()
        );
        final Flow<GroupUpdate, Done, NotUsed> actorAsk = ActorFlow.ask(
            settings.reactiveSync.parallelism,
            registrationRef, settings.reactiveSync.timeOut,
            (g, b) -> {
                if (!(g instanceof GroupUpdate.ArtifactRegistered a)) {
                    return new ArtifactSyncWorker.Ignored(b);
                }
                return new ArtifactSyncWorker.PerformResync(a.coordinates(), b);
            }
        );
        artifactService.groupTopic()
            .subscribe()
            .atLeastOnce(actorAsk);
        // endregion
    }

    private static Behavior<Command> timedSync(
        final ArtifactService artifactService,
        final VersionsService versionsService,
        final ClusterSharding clusterSharding
    ) {
        final AtomicInteger integer = new AtomicInteger();
        return Behaviors.setup((ctx) -> {
            final var dispatch = DispatcherSelector.defaultDispatcher();
            final int i = integer.incrementAndGet();
            final var requester = ctx.spawn(
                Behaviors.supervise(RequestArtifactsToSync.create(artifactService)).onFailure(
                    SupervisorStrategy.restart()),
                String.format("requester-%d-%d", i, System.currentTimeMillis()),
                dispatch
            );
            final var artifactSyncPool = Routers.pool(
                4,
                Behaviors.supervise(ArtifactSyncWorker.create(versionsService, clusterSharding))
                    .onFailure(SupervisorStrategy.restartWithBackoff(Duration.ofSeconds(1), Duration.ofMinutes(10), 0.2))
            );
            final var syncWorker = ctx.spawn(
                artifactSyncPool,
                String.format("pooled-artifact-synchronization-workers-%d-%d", i, System.currentTimeMillis()),
                dispatch
            );
            return passSyncedArtifacts(artifactService, requester, syncWorker);
        });
    }

    private static Behavior<Command> passSyncedArtifacts(
        final ArtifactService artifactService,
        final ActorRef<RequestArtifactsToSync.Command> requester,
        final ActorRef<ArtifactSyncWorker.Command> syncWorker
    ) {
        return Behaviors.setup(ctx -> {
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
                return Behaviors.receive(Command.class)
                    .onMessage(WrappedArtifactsToSync.class, result -> {
                        Source.from(result.artifactCoordinates)
                            .async()
                            .via(registrationFlow.async())
                            .runWith(Sink.ignore(), ctx.getSystem());
                        return Behaviors.same();
                    })
                    .onMessage(GatherGroupArtifacts.class, g -> {
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
                            return new WrappedArtifactsToSync(ok);
                        });
                        return Behaviors.same();
                    })
                    .build();
            }
        );
    }

}
