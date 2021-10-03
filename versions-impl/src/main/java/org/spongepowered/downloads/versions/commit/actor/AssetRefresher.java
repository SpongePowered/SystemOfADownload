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
package org.spongepowered.downloads.versions.commit.actor;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.Behavior;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorFlow;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.VersionExtension;
import org.spongepowered.downloads.versions.collection.ACCommand;
import org.spongepowered.downloads.versions.collection.VersionedArtifactAggregate;
import org.spongepowered.downloads.versions.commit.domain.GitBasedArtifact;
import org.spongepowered.downloads.versions.commit.domain.GitCommand;
import org.spongepowered.downloads.versions.commit.domain.GitEvent;

import java.time.Duration;

/**
 * Performs reactive side-effectual handling of {@link GitBasedArtifact} events
 * such that a {@link GitEvent.RepoRegistered} will back-fill previously
 * registered versions with commit attempts.
 */
public final class AssetRefresher {

    public interface Command {
    }

    public final record Refresh(ArtifactCoordinates coordinates) implements Command {
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

    public static Behavior<Command> refreshVersions() {
        return Behaviors.setup(ctx -> {
            final var config = VersionExtension.Settings.get(ctx.getSystem());
            final var commitWorkers = ctx.spawn(
                VersionedAssetWorker.configure(ctx.getSystem().classicSystem()), "asset-commit-fetcher", DispatcherSelector.defaultDispatcher());
            final Flow<ArtifactCollection, Done, NotUsed> commitFetcherAsk = ActorFlow.ask(
                config.commitFetch.parallelism,
                commitWorkers,
                Duration.ofHours(5),
                VersionedAssetWorker.FetchCommitFromAsset::new
            );
            final var sharding = ClusterSharding.get(ctx.getSystem());
            final var setup = new Setup(ctx, commitFetcherAsk, sharding);

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
                    Source.from(assets.assets())
                        .async()
                        .via(setup.commitFetcherAsk())
                        .runWith(Sink.ignore(), setup.ctx().getSystem());
                    return Behaviors.same();
                })
                .build();
        });
    }

    private static Behavior<Command> kickoffRefresh(final Refresh refresh, final Setup setup) {
        final var findUnmanagedVersions = setup.sharding().entityRefFor(
                GitBasedArtifact.ENTITY_TYPE_KEY, refresh.coordinates.asMavenString())
            .ask(
                GitCommand.GetUnCommittedVersions::new,
                Duration.ofMinutes(1)
            );
        setup.ctx().pipeToSelf(
            findUnmanagedVersions, (response, throwable) -> {
                if (throwable != null) {
                    return new Failed(refresh.coordinates);
                }
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
