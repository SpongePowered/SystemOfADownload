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
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Routers;
import akka.actor.typed.receptionist.Receptionist;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.typed.Cluster;
import akka.japi.Pair;
import akka.stream.javadsl.Flow;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.api.models.VersionedArtifactUpdates;
import org.spongepowered.synchronizer.actor.CommitDetailsRegistrar;
import org.spongepowered.synchronizer.actor.CommitRegistrar;
import org.spongepowered.synchronizer.akka.FlowUtil;
import org.spongepowered.synchronizer.gitmanaged.domain.GitCommand;
import org.spongepowered.synchronizer.gitmanaged.domain.GitManagedArtifact;
import org.spongepowered.synchronizer.gitmanaged.util.jgit.CommitResolutionManager;

import java.time.Duration;
import java.util.UUID;

public class CommitConsumer {

    public static void setupSubscribers(VersionsService versionsService, ActorContext<?> ctx) {
        final var member = Cluster.get(ctx.getSystem()).selfMember();
        final var uid = UUID.randomUUID();
        final var workerName = "commit-resolver-" + uid;
        if (member.hasRole("commit-resolver")) {
            final var registrar = ctx.spawn(CommitRegistrar.register(versionsService), "commit-registrar-" + uid);
            spawnCommitResolver(ctx, uid, workerName, registrar);
        }
        final var versionedAssetFlows = CommitConsumer.createVersionedAssetFlows(ctx);
        versionsService.versionedArtifactUpdatesTopic()
            .subscribe()
            .atLeastOnce(versionedAssetFlows);
    }

    public static Flow<VersionedArtifactUpdates, Done, NotUsed> createVersionedAssetFlows(
        ActorContext<?> ctx
    ) {
        final var sharding = ClusterSharding.get(ctx.getSystem());
        final var associateCommitFlow = registerRawCommitFlow(sharding);

        final var registerResolvedCommits = registerResolvedCommitDetails(sharding);

        return FlowUtil.splitClassFlows(
            Pair.create(VersionedArtifactUpdates.CommitExtracted.class, associateCommitFlow),
            Pair.create(VersionedArtifactUpdates.GitCommitDetailsAssociated.class, registerResolvedCommits)
        );
    }

    private static void spawnCommitResolver(
        ActorContext<?> ctx, UUID uid,
        String workerName,
        final ActorRef<CommitDetailsRegistrar.Command> registrar
    ) {
        final ActorRef<CommitResolutionManager.Command> workerRef;

        final var resolver = CommitResolutionManager.resolveCommit(registrar);
        final var supervisedResolver = Behaviors.supervise(resolver)
            .onFailure(SupervisorStrategy.restartWithBackoff(
                Duration.ofMillis(100),
                Duration.ofSeconds(40),
                0.1
            ));
        final var pool = Routers.pool(4, supervisedResolver);

        workerRef = ctx.spawn(
            pool,
            workerName + "-" + uid,
            DispatcherSelector.defaultDispatcher()
        );
        // Announce it to the cluster
        ctx.getSystem().receptionist().tell(Receptionist.register(CommitResolutionManager.SERVICE_KEY, workerRef));

    }

    private static Flow<VersionedArtifactUpdates.CommitExtracted, Done, NotUsed> registerRawCommitFlow(
        ClusterSharding sharding
    ) {
        return Flow.fromFunction(msg -> sharding.entityRefFor(
                    GitManagedArtifact.ENTITY_TYPE_KEY,
                    msg.coordinates().asArtifactCoordinates().asMavenString()
                )
                .<Done>ask(
                    replyTo -> new GitCommand.RegisterRawCommit(msg.coordinates(), msg.commit(), replyTo),
                    Duration.ofSeconds(20)
                )
                .toCompletableFuture()
                .join()
        );
    }

    private static Flow<VersionedArtifactUpdates.GitCommitDetailsAssociated, Done, NotUsed> registerResolvedCommitDetails(
        ClusterSharding sharding
    ) {
        return Flow.fromFunction(event -> sharding
            .entityRefFor(
                GitManagedArtifact.ENTITY_TYPE_KEY,
                event.coordinates().asArtifactCoordinates().asMavenString()
            )
            .<Done>ask(
                replyTo -> new GitCommand.MarkVersionAsResolved(event.coordinates(), event.commit(), replyTo),
                Duration.ofSeconds(20)
            )
            .toCompletableFuture()
            .join()
        );
    }
}
