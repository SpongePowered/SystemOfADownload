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
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
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
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.artifact.api.event.ArtifactUpdate;
import org.spongepowered.downloads.util.akka.FlowUtil;
import org.spongepowered.synchronizer.SonatypeSynchronizer;
import org.spongepowered.synchronizer.gitmanaged.domain.GitCommand;
import org.spongepowered.synchronizer.gitmanaged.domain.GitManagedArtifact;
import org.spongepowered.synchronizer.gitmanaged.util.jgit.CommitResolutionManager;

import java.time.Duration;

public final class ScheduledCommitResolver {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(Refresh.class),
        @JsonSubTypes.Type(Register.class),
        @JsonSubTypes.Type(CommitResolved.class),
        @JsonSubTypes.Type(WorkCompleted.class),
    })
    sealed interface ScheduledRefresh extends Jsonable {
    }

    @JsonTypeName("refresh")
    record Refresh() implements ScheduledRefresh {
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
        final var scheduler = context.spawn(setup(), "ScheduledCommitResolver");
        final var sync = registerForSync(scheduler);
        artifactService.artifactUpdate()
            .subscribe()
            .atLeastOnce(FlowUtil.splitClassFlows(Pair.create(ArtifactUpdate.ArtifactRegistered.class, sync)));
    }

    private static Flow<ArtifactUpdate.ArtifactRegistered, Done, NotUsed> registerForSync(
        final ActorRef<ScheduledRefresh> scheduler
    ) {
        return ActorFlow.ask(
            scheduler, Duration.ofMinutes(1), (reg, replyTo) -> new Register(reg.coordinates(), replyTo));
    }

    public static Behavior<ScheduledRefresh> setup() {
        return Behaviors.setup(ctx -> Behaviors.withTimers(timers -> Behaviors.receive(ScheduledRefresh.class)
            .onMessage(Register.class, msg -> {
                msg.replyTo.tell(Done.done());
                final var key = Routers.group(CommitResolutionManager.SERVICE_KEY);
                final ActorRef<CommitResolutionManager.Command> workerRef = ctx.spawn(
                    key, "repo-associated-commit-resolver");
                timers.startSingleTimer(new Refresh(), Duration.ofMinutes(1));
                return waiting(workerRef, HashSet.of(msg.coordinates()));
            })
            .build()));
    }

    private static Behavior<ScheduledRefresh> waiting(
        final ActorRef<CommitResolutionManager.Command> workerRef,
        final HashSet<ArtifactCoordinates> artifactsKeepingTracked
    ) {
        return Behaviors.setup(ctx -> Behaviors.withTimers(timers -> {
            final ClusterSharding sharding = ClusterSharding.get(ctx.getSystem());
            final Materializer mat = Materializer.createMaterializer(ctx.getSystem());
            return Behaviors.receive(ScheduledRefresh.class)
                .onMessage(Refresh.class, msg -> {
                    performRefresh(workerRef, artifactsKeepingTracked, ctx, sharding, mat);
                    return working(workerRef, artifactsKeepingTracked);
                })
                .onMessage(Register.class, msg -> {
                    msg.replyTo.tell(Done.done());
                    return waiting(workerRef, artifactsKeepingTracked.add(msg.coordinates()));
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
            .flatMapConcat(work -> {
                final Map<MavenCoordinates, String> tuple2s = work.unresolvedCommits();
                final List<CommitResolutionManager.ResolveCommitDetails> requests = tuple2s
                    .toList().map(
                        (t) -> new CommitResolutionManager.ResolveCommitDetails(
                            t._1(), t._2(), work.repositories(), null)
                    );
                return Source.from(requests);
            });
        final Flow<CommitResolutionManager.ResolveCommitDetails, Done, NotUsed> commitResolverAskFlow = ActorFlow.<CommitResolutionManager.ResolveCommitDetails, CommitResolutionManager.Command, Done>ask(
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
            final var getWork = b.add(workFetcherFlow.async());
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
        final Sink<ScheduledRefresh, NotUsed> sink = ActorSink.actorRef(
            ctx.getSelf(), new WorkCompleted(), t -> {
                ctx.getLog().error("Got an error while resolving commits", t);
                return new WorkCompleted();
            });
        Source.fromGraph(graph)
            .via(Flow.<Done, ScheduledRefresh>fromFunction(d -> new CommitResolved()))
            .to(sink)
            .run(mat);
    }

    private static Behavior<ScheduledRefresh> working(
        ActorRef<CommitResolutionManager.Command> workerRef,
        final HashSet<ArtifactCoordinates> artifactsKeepingTracked
    ) {
        return Behaviors.setup(ctx -> Behaviors.withTimers(timers -> Behaviors.receive(ScheduledRefresh.class)
            .onMessage(Refresh.class, msg -> Behaviors.same())
            .onMessage(Register.class, msg -> {
                msg.replyTo.tell(Done.done());
                return working(workerRef, artifactsKeepingTracked.add(msg.coordinates()));
            })
            .onMessage(CommitResolved.class, msg -> Behaviors.same())
            .onMessage(WorkCompleted.class, msg -> {
                timers.startSingleTimer(new Refresh(), Duration.ofMinutes(1));
                return waiting(workerRef, artifactsKeepingTracked);
            })
            .build()));
    }

    private static Flow<ArtifactCoordinates, GitCommand.UnresolvedWork, NotUsed> getWork(ClusterSharding sharding) {
        return Flow.<ArtifactCoordinates>create()
            .mapAsync(1, artifact -> sharding.entityRefFor(GitManagedArtifact.ENTITY_TYPE_KEY, artifact.asMavenString())
                .ask(GitCommand.GetUnresolvedVersions::new, Duration.ofMinutes(4)))
            .filter(GitCommand.UnresolvedWork::hasWork);
    }

}
