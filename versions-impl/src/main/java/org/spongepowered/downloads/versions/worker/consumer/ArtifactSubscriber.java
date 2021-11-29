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
package org.spongepowered.downloads.versions.worker.consumer;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Routers;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.japi.Pair;
import akka.stream.FlowShape;
import akka.stream.javadsl.Balance;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Merge;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorFlow;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.event.ArtifactUpdate;
import org.spongepowered.downloads.versions.util.akka.FlowUtil;
import org.spongepowered.downloads.versions.util.jgit.CommitResolver;
import org.spongepowered.downloads.versions.worker.domain.gitmanaged.GitCommand;
import org.spongepowered.downloads.versions.worker.domain.gitmanaged.GitManagedArtifact;
import org.spongepowered.downloads.versions.worker.domain.global.GlobalCommand;
import org.spongepowered.downloads.versions.worker.domain.global.GlobalRegistration;

import java.net.URI;
import java.time.Duration;

public final class ArtifactSubscriber {

    public static void setup(ArtifactService artifacts, ActorContext<Void> ctx) {

        final Flow<ArtifactUpdate, Done, NotUsed> flow = generateFlows(ctx);

        artifacts.artifactUpdate()
            .subscribe()
            .atLeastOnce(flow);
    }

    private static Flow<ArtifactUpdate, Done, NotUsed> generateFlows(ActorContext<Void> ctx) {
        final var sharding = ClusterSharding.get(ctx.getSystem());
        final var repoAssociatedFlow = getRepoAssociatedFlow(sharding, ctx);
        final var artifactRegistration = getArtifactRegisteredFlow(sharding);

        return FlowUtil.splitClassFlows(
            Pair.create(ArtifactUpdate.ArtifactRegistered.class, artifactRegistration),
            Pair.create(ArtifactUpdate.GitRepositoryAssociated.class, repoAssociatedFlow)
        );
    }

    @SuppressWarnings("unchecked")
    private static Flow<ArtifactUpdate.GitRepositoryAssociated, Done, NotUsed> getRepoAssociatedFlow(
        ClusterSharding sharding,
        ActorContext<Void> ctx
    ) {
        // Step 1 - Register the repository with GitManagedArtifact
        final var registerRepo = Flow.<ArtifactUpdate.GitRepositoryAssociated>create()
            .mapAsync(
                1, c -> sharding.entityRefFor(GitManagedArtifact.ENTITY_TYPE_KEY, c.coordinates().asMavenString())
                    .<Done>ask(replyTo -> new GitCommand.RegisterRepository(URI.create(c.repository()), replyTo), Duration.ofSeconds(20))
                    .thenApply(d -> c)
            );
        final var key = Routers.group(CommitResolver.SERVICE_KEY);
        final var workerRef = ctx.spawn(key, "repo-associated-commit-resolver");
        // Step 2 - Get unresolved commits from GitManagedArtifact to perform work
        final Flow<ArtifactUpdate.GitRepositoryAssociated, CommitResolver.ResolveCommitDetails, NotUsed> registerRepoFlow = Flow.<ArtifactUpdate.GitRepositoryAssociated>create()
            .mapAsync(
                1, c -> sharding.entityRefFor(GitManagedArtifact.ENTITY_TYPE_KEY, c.coordinates().asMavenString())
                    .ask(GitCommand.GetUnresolvedVersions::new, Duration.ofSeconds(20))
            )
            .flatMapConcat(work -> Source.from(work.unresolvedCommits()
                .toList().map(
                    (t) -> new CommitResolver.ResolveCommitDetails(t._1(), t._2(), work.repositories(), null))));
        // Step 2a - Resolve the commits
        final var flow = ActorFlow.<CommitResolver.ResolveCommitDetails, CommitResolver.Command, Done>ask(
            4,
            workerRef,
            Duration.ofMinutes(10),
            (msg, replyTo) -> new CommitResolver.ResolveCommitDetails(
                msg.coordinates(), msg.commit(), msg.gitRepo(), replyTo)
        );

        return Flow.fromGraph(GraphDSL.create(b -> {
            final var balance = b.add(Balance.<CommitResolver.ResolveCommitDetails>create(4));
            final var zip = b.add(Merge.<Done>create(4));
            final var add = b.add(registerRepo.via(registerRepoFlow));
            b.from(add.out())
                .toFanOut(balance);
            for (int i = 0; i < 4; i++) {
                final var resolver = b.add(flow);
                b.from(balance.out(i))
                    .via(resolver)
                    .toInlet(zip.in(i));

            }
            return FlowShape.of(add.in(), zip.out());
        }));
    }

    private static Flow<ArtifactUpdate.ArtifactRegistered, Done, NotUsed> getArtifactRegisteredFlow(
        ClusterSharding sharding
    ) {
        final var globalRegistration = Flow.<ArtifactUpdate.ArtifactRegistered>create()
            .map(u -> sharding.entityRefFor(GlobalRegistration.ENTITY_TYPE_KEY, "global")
                .<Done>ask(
                    replyTo -> new GlobalCommand.RegisterArtifact(replyTo, u.coordinates()),
                    Duration.ofMinutes(10)
                )
                .toCompletableFuture()
                .join()
            );
        return FlowUtil.broadcast(globalRegistration);
    }

}
