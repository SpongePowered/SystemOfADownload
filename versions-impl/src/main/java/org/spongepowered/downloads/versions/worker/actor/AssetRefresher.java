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
package org.spongepowered.downloads.versions.worker.actor;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Routers;
import akka.actor.typed.receptionist.ServiceKey;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.persistence.typed.PersistenceId;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorFlow;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.server.collection.ACCommand;
import org.spongepowered.downloads.versions.server.collection.VersionedArtifactAggregate;
import org.spongepowered.downloads.versions.worker.VersionConfig;
import org.spongepowered.downloads.versions.worker.actor.global.GlobalCommand;
import org.spongepowered.downloads.versions.worker.actor.global.GlobalRegistration;
import org.spongepowered.downloads.versions.worker.domain.GitBasedArtifact;
import org.spongepowered.downloads.versions.worker.domain.GitCommand;
import org.spongepowered.downloads.versions.worker.domain.GitEvent;

import java.time.Duration;
import java.util.UUID;

/**
 * Performs reactive side-effectual handling of {@link GitBasedArtifact} events
 * such that a {@link GitEvent.RepoRegistered} will back-fill previously
 * registered versions with commit attempts.
 */
public final class AssetRefresher {
    public static final ServiceKey<Command> serviceKey = ServiceKey.create(Command.class, "VersionedAssetRefresher");

    public interface Command {
    }

    public final record Refresh(ArtifactCoordinates coordinates) implements Command {
    }

    private final record GlobalRefresh() implements Command {
    }

    private final record ResyncVersions(List<ArtifactCoordinates> artifacts) implements Command {
    }

    private final record Failed(ArtifactCoordinates coordinates) implements Command {
    }

    private final record FetchedVersions(ArtifactCoordinates artifact, List<MavenCoordinates> coordinates)
        implements Command {
    }

    private final record FetchedAssets(ArtifactCoordinates coordinates, List<ArtifactCollection> assets)
        implements Command {
    }

    private final record Setup(
        ActorContext<Command> ctx,
        Flow<ArtifactCollection, Done, NotUsed> commitFetcherAsk,
        ClusterSharding sharding
    ) {
    }

    public static Behavior<Command> refreshVersions(
        final ArtifactService artifacts, VersionConfig config
    ) {
        return Behaviors.setup(ctx -> Behaviors.withTimers(scheduler -> {
            final var commitFetcherUID = UUID.randomUUID();
            final var commitRouter = Routers.group(VersionedAssetWorker.SERVICE_KEY);
            final var commitWorker = ctx.spawn(commitRouter, "asset-commit-fetcher-" + commitFetcherUID);
            final Flow<ArtifactCollection, Done, NotUsed> commitFetcherAsk = ActorFlow.ask(
                config.commitFetch.parallelism,
                commitWorker,
                Duration.ofHours(5),
                VersionedAssetWorker.FetchCommitFromAsset::new
            );
            final var sharding = ClusterSharding.get(ctx.getSystem());
            final var setup = new Setup(ctx, commitFetcherAsk, sharding);

            ctx.getLog().info("Initializing Versioned Asset Refresher");
            sharding.init(Entity.of(
                GlobalRegistration.ENTITY_TYPE_KEY, context -> GlobalRegistration.create(
                    context.getEntityId(),
                    PersistenceId.of(context.getEntityTypeKey().name(), context.getEntityId())
                )));
            scheduler.startSingleTimer("first", new GlobalRefresh(), Duration.ofSeconds(10));
            scheduler.startTimerWithFixedDelay("schedule", new GlobalRefresh(), Duration.ofMinutes(10));
            return Behaviors.receive(Command.class)
                .onMessage(Refresh.class, refresh -> AssetRefresher.kickoffRefresh(refresh, setup))
                .onMessage(Failed.class, failed -> {
                    ctx.getLog().warn(String.format(
                        "Failed to retrieve versions for artifact %s",
                        failed.coordinates.asMavenString()
                    ));
                    return Behaviors.same();
                })
                .onMessage(FetchedVersions.class, versions -> AssetRefresher.fetchVersionedAssets(versions, setup))
                .onMessage(FetchedAssets.class, assets -> {
                    ctx.getLog().debug("Received assets ({}) for version {}", assets.assets.size(), assets.coordinates);
                    Source.from(assets.assets())
                        .async()
                        .via(setup.commitFetcherAsk())
                        .runWith(Sink.ignore(), setup.ctx().getSystem());
                    return Behaviors.same();
                })
                .onMessage(GlobalRefresh.class, refresh -> {
                    ctx.getLog().info("Starting global refresh");
                    ctx.pipeToSelf(
                        sharding.entityRefFor(GlobalRegistration.ENTITY_TYPE_KEY, "global")
                            .ask(GlobalCommand.GetArtifacts::new, Duration.ofSeconds(10))
                            .toCompletableFuture(),
                        (toSync, throwable) -> {
                            if (throwable != null) {
                                return new GlobalRefresh();
                            }
                            return new ResyncVersions(toSync);
                        }
                    );
                    return Behaviors.same();
                })
                .onMessage(ResyncVersions.class, refresh -> {
                    ctx.getLog().info("Refreshing version commits for artifacts {}", refresh.artifacts);
                    refresh.artifacts()
                        .map(Refresh::new)
                        .forEach(ctx.getSelf()::tell);
                    return Behaviors.same();
                })
                .build();
        }));
    }

    private static Behavior<Command> kickoffRefresh(final Refresh refresh, final Setup setup) {
        final var findUnmanagedVersions = setup.sharding().entityRefFor(
                GitBasedArtifact.ENTITY_TYPE_KEY, refresh.coordinates.asMavenString())
            .ask(
                GitCommand.GetUnCommittedVersions::new,
                Duration.ofMinutes(1)
            );
        setup.ctx().getLog().debug("Received refresh {}", refresh.coordinates);
        setup.ctx().pipeToSelf(
            findUnmanagedVersions, (response, throwable) -> {
                if (throwable != null) {
                    setup.ctx.getLog().warn("Failed to get refresh for " + refresh.coordinates, throwable);
                    return new Failed(refresh.coordinates);
                }
                setup.ctx.getLog().info("Refreshing versions for commit information on {}", response);
                return new FetchedVersions(refresh.coordinates, response);
            });
        return Behaviors.same();
    }

    private static Behavior<Command> fetchVersionedAssets(final FetchedVersions versions, final Setup setup) {
        final var versionedAssets = setup.sharding().entityRefFor(
                VersionedArtifactAggregate.ENTITY_TYPE_KEY,
                versions.artifact.asMavenString()
            )
            .<List<ArtifactCollection>>ask(
                replyTo ->
                    new ACCommand.GetCollections(versions.coordinates, replyTo),
                Duration.ofSeconds(20)
            );
        setup.ctx().pipeToSelf(versionedAssets, (response, throwable) -> {
            if (throwable != null) {
                return new Failed(versions.artifact);
            }
            return new FetchedAssets(versions.artifact, response);
        });
        return Behaviors.same();
    }
}
