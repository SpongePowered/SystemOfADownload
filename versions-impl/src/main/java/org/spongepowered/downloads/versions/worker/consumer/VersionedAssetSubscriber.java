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
import akka.actor.typed.ActorRef;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Routers;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.typed.Cluster;
import akka.japi.Pair;
import akka.japi.function.Function;
import akka.stream.javadsl.Flow;
import akka.stream.typed.javadsl.ActorFlow;
import io.vavr.collection.List;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.api.models.ArtifactUpdate;
import org.spongepowered.downloads.versions.api.models.VersionedArtifactUpdates;
import org.spongepowered.downloads.versions.util.akka.FlowUtil;
import org.spongepowered.downloads.versions.util.jgit.CommitResolver;
import org.spongepowered.downloads.versions.worker.WorkerSpawner;
import org.spongepowered.downloads.versions.worker.domain.gitmanaged.GitCommand;
import org.spongepowered.downloads.versions.worker.domain.gitmanaged.GitManagedArtifact;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.VersionedArtifactCommand;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.VersionedArtifactEntity;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

public class VersionedAssetSubscriber {
    public static void setup(ActorContext<Void> ctx, VersionsService versions, ClusterSharding sharding) {

        final var flows = generateFlows(ctx, sharding);

        versions.artifactUpdateTopic()
            .subscribe()
            .atLeastOnce(flows.versionedArtifactUpdateConsumer);

        versions.versionedArtifactUpdatesTopic()
            .subscribe()
            .atLeastOnce(flows.versionedAssetUpdateConsumer);
    }

    private final record ConsumerFlows(
        Flow<ArtifactUpdate, Done, NotUsed> versionedArtifactUpdateConsumer,
        Flow<VersionedArtifactUpdates, Done, NotUsed> versionedAssetUpdateConsumer
    ) {
    }

    private static ConsumerFlows generateFlows(ActorContext<Void> ctx, ClusterSharding sharding) {

        // region ArtifactVersionRegistered flows
        final var versionedArtifactFlow = artifactVersionRegisteredFlows(sharding);
        // endregion

        // region VersionedAssetUpdate flows
        final var versionedAssetFlow = createVersionedAssetFlows(ctx);
        // endregion

        return new ConsumerFlows(versionedArtifactFlow, versionedAssetFlow);
    }

    private static Flow<VersionedArtifactUpdates, Done, NotUsed> createVersionedAssetFlows(
        ActorContext<Void> ctx
    ) {
        final var member = Cluster.get(ctx.getSystem()).selfMember();
        final ActorRef<CommitResolver.Command> workerRef;
        final var uid = UUID.randomUUID();
        final var workerName = "commit-resolver-" + uid;

        if (member.hasRole("commit-resolver")) {
            final var supervised = Behaviors.supervise(
                    CommitDetailsRegistrar.register()
                )
                .onFailure(SupervisorStrategy.restartWithBackoff(
                    Duration.ofMillis(100),
                    Duration.ofSeconds(40),
                    0.1
                ));
            final var registrar = ctx.spawn(supervised, "commit-details-registrar-" + uid);
            final var resolver = CommitResolver.resolveCommit(registrar);
            final var supervisedResolver = Behaviors.supervise(resolver)
                .onFailure(SupervisorStrategy.restartWithBackoff(
                    Duration.ofMillis(100),
                    Duration.ofSeconds(40),
                    0.1
                ));
            final var pool = Routers.pool(4, supervisedResolver);
            workerRef = WorkerSpawner.spawnRemotableWorker(
                ctx, () -> pool,
                () -> CommitResolver.SERVICE_KEY,
                () -> workerName
            );
        } else {
            final var group = Routers.group(CommitResolver.SERVICE_KEY);
            workerRef = ctx.spawn(group, workerName);
        }
        final var sharding = ClusterSharding.get(ctx.getSystem());
        final var associateCommitFlow = Flow.<VersionedArtifactUpdates.CommitExtracted>create()
            .mapAsync(
                4, msg -> sharding.entityRefFor(GitManagedArtifact.ENTITY_TYPE_KEY,
                        msg.coordinates().asArtifactCoordinates().asMavenString()
                    )
                    .<Done>ask(
                        replyTo -> new GitCommand.RegisterRawCommit(msg.coordinates(), msg.commit(), replyTo),
                        Duration.ofSeconds(20)
                    )
            );
        final var commitExtractedPairNotUsedFlow = Flow.<VersionedArtifactUpdates.CommitExtracted>create().mapAsync(
            1, cmd -> sharding
                .entityRefFor(
                    GitManagedArtifact.ENTITY_TYPE_KEY,
                    cmd.coordinates().asArtifactCoordinates().asMavenString()
                )
                .ask(GitCommand.GetRepositories::new, Duration.ofSeconds(20))
                .thenApply(repos -> Pair.create(cmd, repos))
        );
        final var flow = ActorFlow.<Pair<VersionedArtifactUpdates.CommitExtracted, List<URI>>, CommitResolver.Command, Done>ask(
            4,
            workerRef,
            Duration.ofMinutes(10),
            (msg, replyTo) -> new CommitResolver.ResolveCommitDetails(
                msg.first().coordinates(), msg.first().commit(), msg.second(), replyTo)
        );

        final var registerResolvedCommits = Flow.<VersionedArtifactUpdates.GitCommitDetailsAssociated>create()
            .mapAsync(4, event -> sharding
                .entityRefFor(
                    GitManagedArtifact.ENTITY_TYPE_KEY,
                    event.coordinates().asArtifactCoordinates().asMavenString()
                )
                .<Done>ask(
                    replyTo -> new GitCommand.MarkVersionAsResolved(event.coordinates(), event.commit(), replyTo),
                    Duration.ofSeconds(20)
                )
            );

        final var extractedFlow = FlowUtil.broadcast(commitExtractedPairNotUsedFlow.via(flow), associateCommitFlow);

        return FlowUtil.splitClassFlows(
            Pair.create(VersionedArtifactUpdates.CommitExtracted.class, extractedFlow),
            Pair.create(VersionedArtifactUpdates.GitCommitDetailsAssociated.class, registerResolvedCommits)
        );
    }

    private static Flow<ArtifactUpdate, Done, NotUsed> artifactVersionRegisteredFlows(ClusterSharding sharding) {
        final var versionedAssetKickoff = Flow.<ArtifactUpdate.ArtifactVersionRegistered>create()
            .map(u -> sharding.entityRefFor(
                        VersionedArtifactEntity.ENTITY_TYPE_KEY,
                        u.coordinates().asStandardCoordinates()
                    )
                    .<Done>ask(
                        replyTo -> new VersionedArtifactCommand.Register(u.coordinates(), replyTo), Duration.ofMinutes(1))
                    .toCompletableFuture()
                    .join()
            );

        final var versionRegisteredFlow = FlowUtil.broadcast(versionedAssetKickoff);

        final var registerAssets = Flow.<ArtifactUpdate.VersionedAssetCollectionUpdated>create()
            .map(registerVersionWithVersionedAssets(sharding));
        return FlowUtil.splitClassFlows(
            Pair.apply(
                ArtifactUpdate.ArtifactVersionRegistered.class,
                versionRegisteredFlow
            ),
            Pair.apply(
                ArtifactUpdate.VersionedAssetCollectionUpdated.class,
                registerAssets
            )
        );
    }

    private static Function<ArtifactUpdate.VersionedAssetCollectionUpdated, Done> registerVersionWithVersionedAssets(
        ClusterSharding sharding
    ) {
        return update -> sharding.entityRefFor(
                VersionedArtifactEntity.ENTITY_TYPE_KEY,
                update.collection().coordinates().asStandardCoordinates()
            )
            .<Done>ask(
                replyTo -> new VersionedArtifactCommand.AddAssets(update.artifacts(), replyTo),
                Duration.ofMinutes(1)
            )
            .toCompletableFuture()
            .join();
    }
}
