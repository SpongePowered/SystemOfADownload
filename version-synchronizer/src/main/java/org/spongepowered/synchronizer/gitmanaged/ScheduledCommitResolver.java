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
package org.spongepowered.synchronizer.gitmanaged;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Routers;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.typed.ClusterSingleton;
import akka.cluster.typed.SingletonActor;
import akka.japi.Pair;
import akka.stream.Graph;
import akka.stream.Materializer;
import akka.stream.SourceShape;
import akka.stream.javadsl.Balance;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Merge;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorFlow;
import akka.stream.typed.javadsl.ActorSink;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.artifact.api.event.ArtifactUpdate;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupsResponse;
import org.spongepowered.synchronizer.SonatypeSynchronizer;
import org.spongepowered.synchronizer.akka.FlowUtil;
import org.spongepowered.synchronizer.gitmanaged.domain.GitCommand;
import org.spongepowered.synchronizer.gitmanaged.domain.GitManagedArtifact;
import org.spongepowered.synchronizer.gitmanaged.util.jgit.CommitResolutionManager;

import java.time.Duration;

public final class ScheduledCommitResolver {

    public static final ServiceKey<ScheduledRefresh> SCHEDULED_REFRESH = ServiceKey.create(
        ScheduledRefresh.class, "scheduled-refresh");

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(Refresh.class),
        @JsonSubTypes.Type(Register.class),
        @JsonSubTypes.Type(CommitResolved.class),
        @JsonSubTypes.Type(WorkCompleted.class),
        @JsonSubTypes.Type(Resync.class)
    })
    public sealed interface ScheduledRefresh extends Jsonable {
    }

    @JsonTypeName("refresh")
    public record Refresh() implements ScheduledRefresh {
    }

    @JsonTypeName("resync")
    record Resync() implements ScheduledRefresh {
    }

    @JsonTypeName("register-coordinates")
    record Register(ArtifactCoordinates coordinates, ActorRef<Done> replyTo) implements ScheduledRefresh {
    }

    @JsonTypeName("resolved")
    record CommitResolved() implements ScheduledRefresh {
    }

    @JsonTypeName("completed")
    record WorkCompleted() implements ScheduledRefresh {
    }

    public static void setup(
        ArtifactService artifactService,
        ActorContext<SonatypeSynchronizer.Command> context
    ) {
        final var singleton = SingletonActor.of(setup(artifactService), "ScheduledCommitResolver");
        final var scheduler = ClusterSingleton.get(context.getSystem()).init(singleton);
        final var sync = registerForSync(scheduler);
        artifactService.artifactUpdate()
            .subscribe()
            .atLeastOnce(FlowUtil.splitClassFlows(Pair.create(ArtifactUpdate.ArtifactRegistered.class, sync)));
    }

    private static Source<ArtifactCoordinates, NotUsed> parseResponseIntoArtifacts(
        ArtifactService artifactService,
        GroupsResponse r
    ) {
        final Source<String, NotUsed> groups;
        if (r instanceof GroupsResponse.Available a) {
            groups = Source.from(a.groups.map(Group::getGroupCoordinates));
        } else {
            groups = Source.empty();
        }
        final var groupsToArtifacts = Flow.<String>create()
            .mapConcat(g -> {
                final var join = artifactService.getArtifacts(g).invoke().toCompletableFuture().join();
                if (join instanceof GetArtifactsResponse.ArtifactsAvailable aa) {
                    return aa.artifactIds().map(id -> new ArtifactCoordinates(g, id));
                } else {
                    return List.empty();
                }
            });
        return groups.via(groupsToArtifacts);
    }

    private static Flow<ArtifactUpdate.ArtifactRegistered, Done, NotUsed> registerForSync(
        final ActorRef<ScheduledRefresh> scheduler
    ) {
        return ActorFlow.ask(
            scheduler, Duration.ofMinutes(1), (reg, replyTo) -> new Register(reg.coordinates(), replyTo));
    }

    private static Behavior<ScheduledRefresh> setup(
        final ArtifactService artifactService
    ) {
        return Behaviors.setup(ctx -> Behaviors.withTimers(timers -> {
            ctx.getSystem().receptionist().tell(Receptionist.register(SCHEDULED_REFRESH, ctx.getSelf()));
            ctx.getLog().info("Starting ScheduledCommitResolver");
            timers.startSingleTimer("resync", new Resync(), Duration.ofSeconds(30));
            timers.startPeriodicTimer("refresh", new Refresh(), Duration.ofMinutes(1));
            ctx.getLog().info("Scheduled refresh every minute");

            return Behaviors.receive(ScheduledRefresh.class)
                .onMessage(Register.class, msg -> {
                    msg.replyTo.tell(Done.done());
                    final var key = Routers.group(CommitResolutionManager.SERVICE_KEY);
                    final ActorRef<CommitResolutionManager.Command> workerRef = ctx.spawn(
                        key, "repo-associated-commit-resolver");
                    return waiting(workerRef, HashSet.of(msg.coordinates()), artifactService);
                })
                .onMessage(Refresh.class, msg -> Behaviors.same())
                .onMessage(Resync.class, msg -> resyncArtifactCoordinates(artifactService, ctx))
                .build();
        }));
    }

    private static Behavior<ScheduledRefresh> resyncArtifactCoordinates(
        ArtifactService artifactService, ActorContext<ScheduledRefresh> ctx
    ) {
        final Flow<ArtifactService, GroupsResponse, NotUsed> getGroups = Flow.fromFunction(s -> s.getGroups()
            .invoke()
            .toCompletableFuture()
            .join());
        Source.single(artifactService)
            .via(getGroups
                .flatMapConcat((GroupsResponse r) -> parseResponseIntoArtifacts(artifactService, r)))
            .via(ActorFlow.ask(ctx.getSelf(), Duration.ofSeconds(10), Register::new))
            .to(Sink.ignore())
            .run(ctx.getSystem());
        return Behaviors.same();
    }

    private static Behavior<ScheduledRefresh> waiting(
        final ActorRef<CommitResolutionManager.Command> workerRef,
        final HashSet<ArtifactCoordinates> artifactsKeepingTracked,
        final ArtifactService artifactService
    ) {
        return Behaviors.setup(ctx -> Behaviors.withTimers(timers -> {
            final ClusterSharding sharding = ClusterSharding.get(ctx.getSystem());
            final Materializer mat = Materializer.createMaterializer(ctx.getSystem());
            return Behaviors.receive(ScheduledRefresh.class)
                .onMessage(Resync.class, msg -> resyncArtifactCoordinates(artifactService, ctx))
                .onMessage(Refresh.class, msg -> {
                    performRefresh(workerRef, artifactsKeepingTracked, ctx, sharding, mat);
                    return working(workerRef, artifactsKeepingTracked, artifactService);
                })
                .onMessage(Register.class, msg -> {
                    msg.replyTo.tell(Done.done());
                    return waiting(workerRef, artifactsKeepingTracked.add(msg.coordinates()), artifactService);
                })
                .build();
        }));
    }

    @SuppressWarnings("unchecked")
    private static void performRefresh(
        final ActorRef<CommitResolutionManager.Command> workerRef,
        final HashSet<ArtifactCoordinates> artifactsKeepingTracked,
        final ActorContext<ScheduledRefresh> ctx,
        ClusterSharding sharding,
        Materializer mat
    ) {
        final Source<ArtifactCoordinates, NotUsed> artifactsToWorkOn = Source.from(artifactsKeepingTracked);
        final Flow<ArtifactCoordinates, GitCommand.UnresolvedWork, NotUsed> workFetcherFlow = getWork(sharding).log(
            "work-fetcher");
        final Flow<GitCommand.UnresolvedWork, CommitResolutionManager.ResolveCommitDetails, NotUsed> parseWorkFlow = Flow.<GitCommand.UnresolvedWork>create()
            .log("parse-work")
            .flatMapConcat(work -> {
                final Map<MavenCoordinates, String> tuple2s = work.unresolvedCommits();
                final List<CommitResolutionManager.ResolveCommitDetails> requests = tuple2s
                    .toList().map(
                        (t) -> new CommitResolutionManager.ResolveCommitDetails(
                            t._1(), t._2(), work.repositories(), null)
                    );
                return Source.from(requests);
            });
        final Flow<CommitResolutionManager.ResolveCommitDetails, Done, NotUsed> commitResolverAskFlow = ActorFlow.ask(
            4,
            workerRef,
            Duration.ofMinutes(10),
            (m, replyTo) -> new CommitResolutionManager.ResolveCommitDetails(
                m.coordinates(), m.commit(), m.gitRepo(), replyTo)
        );
        final Graph<SourceShape<Done>, NotUsed> graph = GraphDSL.create(b -> {
            final SourceShape<ArtifactCoordinates> artifacts = b.add(artifactsToWorkOn);
            final var balance = b.add(Balance.<CommitResolutionManager.ResolveCommitDetails>create(4));
            final var zip = b.add(Merge.<Done>create(4));
            final var getWork = b.add(workFetcherFlow);
            final var parseWork = b.add(parseWorkFlow.async());
            b.from(artifacts.out())
                .via(getWork)
                .via(parseWork)
                .toFanOut(balance);
            for (int i = 0; i < 4; i++) {
                final var resolver = b.add(commitResolverAskFlow.async());
                b.from(balance.out(i))
                    .via(resolver)
                    .toInlet(zip.in(i));
            }
            return SourceShape.of(zip.out());
        });
        final var log = ctx.getLog();
        final Sink<ScheduledRefresh, NotUsed> sink = ActorSink.actorRef(
            ctx.getSelf(), new WorkCompleted(), t -> {
                log.error("Got an error while resolving commits", t);
                return new WorkCompleted();
            });
        Source.fromGraph(graph)
            .via(Flow.<Done, ScheduledRefresh>fromFunction(d -> new CommitResolved()))
            .to(sink)
            .run(mat);
    }

    private static Behavior<ScheduledRefresh> working(
        ActorRef<CommitResolutionManager.Command> workerRef,
        final HashSet<ArtifactCoordinates> artifactsKeepingTracked,
        final ArtifactService artifactService
    ) {
        return Behaviors.setup(ctx -> Behaviors.withTimers(timers -> Behaviors.receive(ScheduledRefresh.class)
            .onMessage(Resync.class, msg -> resyncArtifactCoordinates(artifactService, ctx))
            .onMessage(Refresh.class, msg -> Behaviors.same())
            .onMessage(Register.class, msg -> {
                msg.replyTo.tell(Done.done());
                return working(workerRef, artifactsKeepingTracked.add(msg.coordinates()), artifactService
                );
            })
            .onMessage(CommitResolved.class, msg -> Behaviors.same())
            .onMessage(WorkCompleted.class, msg -> {
                timers.startSingleTimer(new Refresh(), Duration.ofMinutes(1));
                return waiting(workerRef, artifactsKeepingTracked, artifactService);
            })
            .build()));
    }

    private static Flow<ArtifactCoordinates, GitCommand.UnresolvedWork, NotUsed> getWork(ClusterSharding sharding) {
        return Flow.<ArtifactCoordinates>create()
            .map(artifact -> sharding.entityRefFor(GitManagedArtifact.ENTITY_TYPE_KEY, artifact.asMavenString())
                .ask(GitCommand.GetUnresolvedVersions::new, Duration.ofSeconds(30))
                .toCompletableFuture()
                .join())
            .filter(GitCommand.UnresolvedWork::hasWork);
    }

}
