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
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.japi.Pair;
import akka.stream.FlowShape;
import akka.stream.UniformFanOutShape;
import akka.stream.javadsl.Balance;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Merge;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.artifact.api.event.ArtifactUpdate;
import org.spongepowered.downloads.versions.server.domain.ACCommand;
import org.spongepowered.downloads.versions.server.domain.VersionedArtifactAggregate;
import org.spongepowered.downloads.versions.util.akka.FlowUtil;
import org.spongepowered.downloads.versions.worker.domain.global.GlobalCommand;
import org.spongepowered.downloads.versions.worker.domain.global.GlobalRegistration;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.VersionedArtifactCommand;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.VersionedArtifactEntity;

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
        final var repoAssociatedFlow = getRepoAssociatedFlow(sharding);
        final var artifactRegistration = getArtifactRegisteredFlow(sharding);

        return FlowUtil.splitClassFlows(
            Pair.create(ArtifactUpdate.ArtifactRegistered.class, artifactRegistration),
            Pair.create(ArtifactUpdate.GitRepositoryAssociated.class, repoAssociatedFlow)
        );
    }

    @SuppressWarnings("unchecked")
    private static Flow<ArtifactUpdate.GitRepositoryAssociated, Done, NotUsed> getRepoAssociatedFlow(
        ClusterSharding sharding
    ) {
        final var getArtifactVersionsFlow = Flow.<ArtifactUpdate.GitRepositoryAssociated>create()
            .map(u -> sharding.entityRefFor(VersionedArtifactAggregate.ENTITY_TYPE_KEY, u.coordinates().asMavenString())
                .ask(ACCommand.GetVersions::new, Duration.ofMinutes(20))
                .thenApply(c -> Pair.create(u.repository(), c))
                .toCompletableFuture()
                .join()
            )
            .mapConcat(p -> p.second().map(v -> Pair.create(p.first(), v)));
        final var registerRepoFlow = Flow.<Pair<String, MavenCoordinates>>create()
            .map(c -> sharding.entityRefFor(VersionedArtifactEntity.ENTITY_TYPE_KEY, c.second().asStandardCoordinates())
                .<Done>ask(r -> new VersionedArtifactCommand.RegisterRepo(c.second(), c.first(), r), Duration.ofMinutes(20))
                .toCompletableFuture()
                .join()
            );

        return Flow.fromGraph(GraphDSL.create(b -> {
            final UniformFanOutShape<Pair<String, MavenCoordinates>, Pair<String, MavenCoordinates>> balance = b.add(Balance.create(10));
            final FlowShape<ArtifactUpdate.GitRepositoryAssociated, Pair<String, MavenCoordinates>> ingest = b.add(
                getArtifactVersionsFlow);

            b.from(ingest.out())
                .toFanOut(balance);
            final var zip = b.add(Merge.<Done>create(10));
            for (int i = 0; i < 10; i++) {
                final var register = b.add(registerRepoFlow);
                b.from(balance.out(i))
                    .via(register)
                    .toInlet(zip.in(i));
            }
            return FlowShape.of(ingest.in(), zip.out());
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
