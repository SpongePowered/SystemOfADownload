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
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.ServiceKey;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.stream.ClosedShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.RunnableGraph;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorFlow;
import io.vavr.collection.List;
import io.vavr.collection.SortedMap;
import io.vavr.collection.TreeMap;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.server.collection.ACCommand;
import org.spongepowered.downloads.versions.worker.VersionConfig;
import org.spongepowered.downloads.versions.worker.actor.global.GlobalCommand;
import org.spongepowered.downloads.versions.worker.domain.GitBasedArtifact;
import org.spongepowered.downloads.versions.worker.domain.GitCommand;
import org.spongepowered.downloads.versions.worker.domain.GitEvent;

import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Performs reactive side-effectual handling of {@link GitBasedArtifact} events
 * such that a {@link GitEvent.RepoRegistered} will back-fill previously
 * registered versions with commit attempts.
 */
public final class AssetRefresher {
    public static final ServiceKey<Command> serviceKey = ServiceKey.create(Command.class, "VersionedAssetRefresher");
    private static final Comparator<ArtifactCoordinates> COORDINATES_COMPARATOR = Comparator.comparing(
        ArtifactCoordinates::asMavenString);

    public interface Command {
    }

    public final record Refresh(ArtifactCoordinates coordinates) implements Command {
    }

    private final record GlobalRefresh() implements Command {
    }

    public final record FetchedVersions(ArtifactCoordinates artifact, List<MavenCoordinates> coordinates)
        implements Command {
    }

    public final record FetchedAssets(ArtifactCoordinates coordinates, List<ArtifactCollection> assets)
        implements Command {
    }

    private final record Completed(List<MavenCoordinates> coordinates) implements Command {
    }

    private final record Setup(
        ActorContext<Command> ctx,
        ActorRef<ACCommand> collectionFetcher,
        ClusterSharding sharding,
        ActorRef<GlobalCommand> getter,
        Flow<ArtifactCollection, Done, NotUsed> commitFetcherAsk
    ) {
    }

    public static Behavior<Command> refreshVersions(
        VersionConfig config,
        ActorRef<VersionedAssetWorker.Command> worker,
        ActorRef<GlobalCommand> getter,
        ActorRef<ACCommand> collectionFetcher
    ) {
        return Behaviors.setup(ctx -> Behaviors.withTimers(scheduler -> {
            final Flow<ArtifactCollection, Done, NotUsed> commitFetcherAsk = ActorFlow.ask(
                config.commitFetch.parallelism,
                worker,
                Duration.ofHours(5),
                VersionedAssetWorker.FetchCommitFromAsset::new
            );
            final var sharding = ClusterSharding.get(ctx.getSystem());
            final var setup = new Setup(ctx, collectionFetcher, sharding, getter, commitFetcherAsk);
            ctx.getLog().info("Initializing Versioned Asset Refresher");
            scheduler.startSingleTimer("first", new GlobalRefresh(), Duration.ofSeconds(10));
            return idling(setup);
        }));
    }

    private static Behavior<Command> idling(Setup setup) {
        return Behaviors.receive(Command.class)
            .onMessage(GlobalRefresh.class, g -> {
                setup.ctx.getLog().info("Starting global refresh");
                final var future = ask(setup.getter, setup.ctx);
                setup.ctx.pipeToSelf(
                    future,
                    (toSync, throwable) -> {
                        if (throwable != null) {
                            return g;
                        }
                        return new ResyncVersions(toSync);
                    }
                );
                return refreshing(setup);
            })
            .onMessage(Refresh.class, refresh -> {
                kickoffRefresh(refresh.coordinates, setup);
                return fetchingVersionsToWorkOn(setup,
                    TreeMap.of(COORDINATES_COMPARATOR, refresh.coordinates, List.empty()),
                    TreeMap.empty(COORDINATES_COMPARATOR));
            })
            .build();
    }
    private final record Failed(ArtifactCoordinates coordinates) implements Command {
    }

    private final record ResyncVersions(List<ArtifactCoordinates> artifacts) implements Command {
    }
    private static Behavior<Command> refreshing(
        final Setup setup
    ) {
        return Behaviors.setup(ctx -> {
            return Behaviors.receive(Command.class)
                .onMessage(GlobalRefresh.class, dupe -> Behaviors.same())
                .onMessage(ResyncVersions.class, refresh -> {
                    ctx.getLog().info("Refreshing version commits for artifacts {}", refresh.artifacts.size());

                    if (refresh.artifacts.isEmpty()) {
                        return idling(setup);
                    }
                    final var head = refresh.artifacts.head();
                    final var tail = refresh.artifacts.tail();
                    kickoffRefresh(head, setup);
                    return fetchingVersionsToWorkOn(
                        setup,
                        TreeMap.of(COORDINATES_COMPARATOR, head, List.empty()),
                        tail.toSortedMap(COORDINATES_COMPARATOR, Function.identity(), c -> List.empty())
                    );
                })
                .build();
        });
    }

    private static Behavior<Command> fetchingVersionsToWorkOn(
        final Setup setup,
        final SortedMap<ArtifactCoordinates, List<MavenCoordinates>> working,
        final SortedMap<ArtifactCoordinates, List<MavenCoordinates>> queue
    ) {
        return Behaviors.receive(Command.class)
            .onMessage(GlobalRefresh.class, refresh -> {
                return Behaviors.same();
            })
            .onMessage(Refresh.class, refresh -> {
                if (working.containsKey(refresh.coordinates) || queue.containsKey(refresh.coordinates)) {
                    return Behaviors.same();
                }
                kickoffRefresh(refresh.coordinates, setup);
                return fetchingVersionsToWorkOn(
                    setup,
                    working,
                    queue.put(refresh.coordinates, List.empty())
                );
            })
            .onMessage(FetchedVersions.class, fetched -> {
                fetchVersionedAssets(fetched, setup);
                final var nowWorkingOn = working.put(fetched.artifact, fetched.coordinates, List::appendAll);
                return fetchingAssetsForVersions(setup, nowWorkingOn, TreeMap.empty(MavenCoordinates::compareTo), queue);
            })
            .build();
    }

    private static Behavior<Command> fetchingAssetsForVersions(
        final Setup setup,
        final SortedMap<ArtifactCoordinates, List<MavenCoordinates>> working,
        final SortedMap<MavenCoordinates, ArtifactCollection> assets,
        final SortedMap<ArtifactCoordinates, List<MavenCoordinates>> queue
    ) {
        return Behaviors.receive(Command.class)
            .onMessage(GlobalRefresh.class, refresh -> {
                return Behaviors.same();
            })
            .onMessage(Refresh.class, refresh -> {
                return Behaviors.same();
            })
            .onMessage(FetchedAssets.class, fetchedAssets -> {
                final var retrievedAssets = fetchedAssets.assets.toMap(ArtifactCollection::coordinates, Function.identity());
                final var filtered = retrievedAssets.filter(a -> assets.containsValue(a._2));
                final var from = Source.from(filtered.values());
                RunnableGraph.fromGraph(GraphDSL.create(b -> {
                    final var input = b.add(from);
                    final var flow = b.add(setup.commitFetcherAsk);
                    final var sink = b.add(Sink.ignore());
                    b.from(input.out())
                        .via(flow)
                        .toInlet(sink.in());
                    return ClosedShape.getInstance();
                }));
            })
            .onMessage(FetchedVersions.class, fetched -> {
                final var workingVersions = working.get(fetched.artifact).getOrElse(List.empty());
                final var queuedVersions = queue.get(fetched.artifact).getOrElse(List.empty());
                final var unaccountedVersions = fetched.coordinates.filter(Predicate.not(workingVersions::contains))
                    .filter(Predicate.not(queuedVersions::contains));
                return workingVersions
                    .<Behavior<Command>>map(coords -> {
                        if (unaccountedVersions.isEmpty()) {
                            return Behaviors.same();
                        }
                        return fetchingAssetsForVersions(
                            setup, working, assets,
                            queue.put(fetched.artifact, queuedVersions.appendAll(unaccountedVersions))
                        );
                    })
                    .getOrElse(() ->
                        fetchingAssetsForVersions(
                            setup,
                            working, assets, queue.put(fetched.artifact, fetched.coordinates)
                        )
                    );
            })
            .build();
    }

    private static CompletionStage<List<ArtifactCoordinates>> ask(
        ActorRef<GlobalCommand> getter, ActorContext<Command> ctx
    ) {
        return AskPattern.ask(
            getter,
            GlobalCommand.GetArtifacts::new,
            Duration.ofSeconds(20),
            ctx.getSystem().scheduler()
        );
    }

    /**
     * Kick off a refresh of the given coordinates. Expects to pipe back
     * {@link Failed} or {@link FetchedVersions}.
     *
     * @param coords The artifact to get versions to work on
     * @param setup The setup object
     */
    private static void kickoffRefresh(final ArtifactCoordinates coords, final Setup setup) {
        final var findUnmanagedVersions = setup.sharding()
            .entityRefFor(GitBasedArtifact.ENTITY_TYPE_KEY, coords.asMavenString())
            .ask(
                GitCommand.GetUnCommittedVersions::new,
                Duration.ofMinutes(1)
            );
        setup.ctx().getLog().trace("Received refresh {}", coords);
        setup.ctx().pipeToSelf(
            findUnmanagedVersions, (response, throwable) -> {
                if (throwable != null) {
                    setup.ctx.getLog().warn("Failed to get refresh for " + coords, throwable);
                    return new Failed(coords);
                }
                setup.ctx.getLog().debug("Refreshing versions for commit information on {}", response.size());
                return new FetchedVersions(coords, response);
            });
    }

    private static void fetchVersionedAssets(final FetchedVersions versions, final Setup setup) {
        final var versionedAssets = AskPattern.<ACCommand, List<ArtifactCollection>>ask(
            setup.collectionFetcher,
            replyTo -> new ACCommand.GetCollections(versions.coordinates, replyTo),
            Duration.ofSeconds(20),
            setup.ctx.getSystem().scheduler()
        );
        setup.ctx().pipeToSelf(versionedAssets, (response, throwable) -> {
            if (throwable != null) {
                return new Failed(versions.artifact);
            }
            final var toSync = response.filter(col -> versions.coordinates.contains(col.coordinates()));
            setup.ctx.getLog().trace(
                "Found versions for {} to sync {}", versions.artifact.asMavenString(),
                toSync.map(ArtifactCollection::coordinates).map(v -> v.version)
            );
            if (toSync.isEmpty()) {
                return new Completed(versions.coordinates);
            }
            return new FetchedAssets(versions.artifact, toSync);
        });
    }
}
