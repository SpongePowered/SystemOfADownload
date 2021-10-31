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
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Routers;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.cluster.Cluster;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.japi.Pair;
import akka.persistence.typed.PersistenceId;
import akka.stream.javadsl.Flow;
import akka.stream.typed.javadsl.ActorFlow;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.api.models.VersionedArtifactUpdates;
import org.spongepowered.downloads.versions.worker.actor.ArtifactRetriever;
import org.spongepowered.downloads.versions.worker.actor.ArtifactSubscriber;
import org.spongepowered.downloads.versions.worker.actor.AssetRefresher;
import org.spongepowered.downloads.versions.worker.actor.AssetRetriever;
import org.spongepowered.downloads.versions.worker.actor.CommitDetailsRegistrar;
import org.spongepowered.downloads.versions.worker.actor.CommitExtractor;
import org.spongepowered.downloads.versions.worker.actor.CommitResolver;
import org.spongepowered.downloads.versions.worker.actor.VersionedAssetWorker;
import org.spongepowered.downloads.versions.worker.actor.global.GlobalRegistration;
import org.spongepowered.downloads.versions.worker.akka.FlowUtil;
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

    public static Behavior<Void> create(
        final ArtifactService artifacts,
        final VersionsService versions
    ) {
        return Behaviors.setup(ctx -> {
            final ClusterSharding sharding = ClusterSharding.get(ctx.getSystem());
            sharding.init(
                Entity.of(
                    GitBasedArtifact.ENTITY_TYPE_KEY,
                    GitBasedArtifact::create
                )
            );
            sharding.init(
                Entity.of(
                    GlobalRegistration.ENTITY_TYPE_KEY,
                    context -> GlobalRegistration.create(context.getEntityId(), PersistenceId.of(
                        context.getEntityTypeKey().name(),
                        context.getEntityId()
                    ))
                )
            );
            ArtifactSubscriber.setup(artifacts, ctx);
            final var member = Cluster.get(ctx.getSystem()).selfMember();
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
                if (member.hasRole("asset-fetcher")) {
                    spawnWorker(
                        ctx, () -> VersionedAssetWorker.commitFetcher(versionConfig.commitFetch),
                        () -> VersionedAssetWorker.SERVICE_KEY, () -> "asset-commit-fetcher-" + commitFetcherUID
                    );
                }
                if (member.hasRole("file-extractor")) {
                    spawnWorker(
                        ctx, CommitExtractor::extractCommitFromAssets, () -> CommitExtractor.SERVICE_KEY,
                        () -> "file-commit-worker-" + commitFetcherUID
                    );
                }
                if (member.hasRole("refresher")) {
                    spawnVersionRefresher(ctx, versionConfig, commitFetcherUID);
                }
                if (member.hasRole("commit-resolver")) {
                    spawnCommitResolver(versions, ctx, commitFetcherUID);
                }
            }
            return Behaviors.receive(Void.class)
                .build();
        });

    }

    private static void spawnVersionRefresher(
        ActorContext<Void> ctx, VersionConfig versionConfig, UUID commitFetcherUID
    ) {
        final var register = Behaviors.supervise(ArtifactRetriever.getArtifacts())
            .onFailure(SupervisorStrategy.restart());
        final var retriever = ctx.spawn(register, "global-retriever");
        final var commitRouter = Routers.group(VersionedAssetWorker.SERVICE_KEY);
        final var commitWorker = ctx.spawn(commitRouter, "asset-commit-refresher-" + commitFetcherUID);

        final var collectionFetcher = Behaviors.supervise(AssetRetriever.retrieveAssetCollection())
            .onFailure(SupervisorStrategy.resume());
        final var fetcher = ctx.spawn(collectionFetcher, "global-collection-retriever");
        spawnWorker(
            ctx, () -> AssetRefresher.refreshVersions(versionConfig, commitWorker, retriever, fetcher),
            () -> AssetRefresher.serviceKey,
            () -> "asset-refresher-" + commitFetcherUID
        );
    }

    private static void spawnCommitResolver(VersionsService versions, ActorContext<Void> ctx, UUID commitFetcherUID) {
        final var supervised = Behaviors.supervise(CommitDetailsRegistrar.register())
            .onFailure(
                SupervisorStrategy.restartWithBackoff(Duration.ofMillis(100), Duration.ofSeconds(40), 0.1));

        final var registrar = ctx.spawn(supervised, "commit-details-registrar-" + commitFetcherUID);
        final var workerRef = spawnWorker(
            ctx, () -> CommitResolver.resolveCommit(registrar),
            () -> CommitResolver.SERVICE_KEY,
            () -> "commit-resolver-" + commitFetcherUID
        );
        final var flow = ActorFlow.<VersionedArtifactUpdates.CommitExtracted, CommitResolver.Command, Done>ask(
            1,
            workerRef,
            Duration.ofMinutes(1),
            (msg, replyTo) -> new CommitResolver.ResolveCommitDetails(
                msg.coordinates(), msg.commit(), msg.gitRepositories(), replyTo)
        );
        final var actorFlow = FlowUtil.<VersionedArtifactUpdates>splitClassFlows(
            Pair.create(VersionedArtifactUpdates.CommitExtracted.class, flow)
        );
        versions.versionedArtifactUpdatesTopic()
            .subscribe()
            .atLeastOnce(actorFlow);
    }

    private static <A> ActorRef<A> spawnWorker(
        ActorContext<Void> ctx, Supplier<Behavior<A>> behaviorSupplier, Supplier<ServiceKey<A>> keyProvider,
        Supplier<String> workerName
    ) {
        final var behavior = behaviorSupplier.get();
        final var assetRefresher = Behaviors.supervise(behavior)
            .onFailure(SupervisorStrategy.resume());
        final var name = workerName.get();

        final var workerRef = ctx.spawn(
            assetRefresher,
            name,
            DispatcherSelector.defaultDispatcher()
        );
        ctx.getSystem().receptionist().tell(Receptionist.register(keyProvider.get(), workerRef));
        return workerRef;
    }

}
