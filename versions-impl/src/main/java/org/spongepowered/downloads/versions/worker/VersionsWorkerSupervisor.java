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
package org.spongepowered.downloads.versions.worker;

import akka.Done;
import akka.actor.typed.Behavior;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.stream.javadsl.Flow;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.worker.actor.ArtifactSubscriber;
import org.spongepowered.downloads.versions.worker.actor.AssetRefresher;
import org.spongepowered.downloads.versions.worker.actor.CommitExtractor;
import org.spongepowered.downloads.versions.worker.actor.VersionedAssetWorker;
import org.spongepowered.downloads.versions.worker.domain.GitBasedArtifact;
import org.spongepowered.downloads.versions.worker.domain.GitCommand;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The "reactive" side of Versions where it performs all the various tasks
 * involved with managing Artifact Versions and their artifact assets with
 * relation to those versions. Crucially, this performs the various sync jobs
 * required to derive a Version from an artifact, as well as the various
 * metadata with that version, such as assets and the commit information for
 * that asset. This is mostly a guardian actor, one that wires up children
 * actors to perform the actual work against topic subscribers, either from
 * {@link ArtifactService#artifactUpdate()} or
 * {@link VersionsService#artifactUpdateTopic()}.
 * <p>The important reasoning why this is split out from the Version Service
 * implementation is that this particular supervisor may well be able to handle
 * updates while the VersionsService implementation is the "organizer" of
 * root information.
 */
public final class VersionsWorkerSupervisor {

    public interface Command {
    }

    private static final ServiceKey<Command> key = ServiceKey.create(Command.class, "VersionsWorkerSupervisor");


    public static Behavior<Command> create(
        final ArtifactService artifacts,
        final VersionsService versions
    ) {
        return Behaviors.setup(ctx -> {
            ctx.getSystem().receptionist().tell(Receptionist.register(key, ctx.getSelf()));
            final ClusterSharding sharding = ClusterSharding.get(ctx.getSystem());
            sharding.init(
                Entity.of(
                    GitBasedArtifact.ENTITY_TYPE_KEY,
                    GitBasedArtifact::create
                )
            );
            ctx.spawn(Behaviors.supervise(ArtifactSubscriber.consumeMessages(artifacts))
                .onFailure(SupervisorStrategy.restart()), "versions-worker-artifacts-subscriber");

            versions.artifactUpdateTopic()
                .subscribe()
                .atLeastOnce(
                    Flow.<org.spongepowered.downloads.versions.api.models.ArtifactUpdate>create().map(update -> {
                        if (update instanceof org.spongepowered.downloads.versions.api.models.ArtifactUpdate.ArtifactVersionRegistered gra) {
                            return sharding.entityRefFor(
                                    GitBasedArtifact.ENTITY_TYPE_KEY,
                                    gra.coordinates().asArtifactCoordinates().asMavenString()
                                )
                                .<Done>ask(
                                    replyTo -> new GitCommand.RegisterVersion(gra.coordinates(), replyTo),
                                    Duration.ofMinutes(1)
                                )
                                .toCompletableFuture()
                                .join();
                        }
                        return Done.done();
                    }));


            final var system = ctx.getSystem();
            // Set up the usual actors
            final var versionConfig = VersionExtension.Settings.get(system);
            final var poolSizePerInstance = versionConfig.commitFetch.poolSize;

            for (int i = 0; i < poolSizePerInstance; i++) {
                final var commitFetcherUID = UUID.randomUUID();
                spawnWorker(
                    ctx, () -> VersionedAssetWorker.commitFetcher(versionConfig.commitFetch),
                    () -> VersionedAssetWorker.SERVICE_KEY, () -> "asset-commit-fetcher-" + commitFetcherUID
                );
                spawnWorker(
                    ctx, CommitExtractor::extractCommitFromAssets, () -> CommitExtractor.SERVICE_KEY,
                    () -> "file-commit-worker-" + commitFetcherUID
                );
                spawnWorker(
                    ctx, () -> AssetRefresher.refreshVersions(artifacts, versionConfig), () -> AssetRefresher.serviceKey,
                    () -> "asset-refresher-" + commitFetcherUID
                );
            }
            return Behaviors.receive(Command.class)
                .build();
        });

    }

    private static <A> void spawnWorker(
        ActorContext<Command> ctx, Supplier<Behavior<A>> behaviorSupplier, Supplier<ServiceKey<A>> keyProvider,
        Supplier<String> workerName
    ) {
        final var behavior = behaviorSupplier.get();
        final var assetRefresher = Behaviors.supervise(behavior)
            .onFailure(SupervisorStrategy.restartWithBackoff(Duration.ofSeconds(10), Duration.ofHours(1), 0.5));
        final var name = workerName.get();

        final var workerRef = ctx.spawn(
            assetRefresher,
            name,
            DispatcherSelector.defaultDispatcher()
        );
        ctx.getSystem().receptionist().tell(Receptionist.register(keyProvider.get(), workerRef));
    }


}
