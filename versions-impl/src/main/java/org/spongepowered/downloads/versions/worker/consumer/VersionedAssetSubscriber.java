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
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.japi.Pair;
import akka.stream.javadsl.Flow;
import org.spongepowered.downloads.util.akka.FlowUtil;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.api.models.ArtifactUpdate;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.VersionedArtifactCommand;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.VersionedArtifactEntity;

import java.time.Duration;

public class VersionedAssetSubscriber {
    public static void setup(VersionsService versions, ClusterSharding sharding) {

        // region ArtifactVersionRegistered flows
        final var versionedArtifactFlow = createVersionRegistrationFlows(sharding);
        // endregion

        versions.artifactUpdateTopic()
            .subscribe()
            .atLeastOnce(versionedArtifactFlow);

    }

    private static Flow<ArtifactUpdate, Done, NotUsed> createVersionRegistrationFlows(ClusterSharding sharding) {
        final var registerVersionFlow = createRegisterVersionFlow(sharding);
        final var registerAssets = createRegisterAssetsFlow(sharding);
        return FlowUtil.splitClassFlows(
            Pair.apply(
                ArtifactUpdate.ArtifactVersionRegistered.class,
                registerVersionFlow
            ),
            Pair.apply(
                ArtifactUpdate.VersionedAssetCollectionUpdated.class,
                registerAssets
            )
        );
    }

    private static Flow<ArtifactUpdate.ArtifactVersionRegistered, Done, NotUsed> createRegisterVersionFlow(
        ClusterSharding sharding
    ) {
        return Flow.fromFunction(u -> sharding.entityRefFor(
                    VersionedArtifactEntity.ENTITY_TYPE_KEY,
                    u.coordinates().asStandardCoordinates()
                )
                .<Done>ask(
                    replyTo -> new VersionedArtifactCommand.Register(u.coordinates(), replyTo), Duration.ofMinutes(20))
                .toCompletableFuture()
                .join()
        );
    }

    private static Flow<ArtifactUpdate.VersionedAssetCollectionUpdated, Done, NotUsed> createRegisterAssetsFlow(
        ClusterSharding sharding
    ) {
        return Flow.fromFunction(update -> sharding.entityRefFor(
                    VersionedArtifactEntity.ENTITY_TYPE_KEY,
                    update.collection().coordinates().asStandardCoordinates()
                )
                .<Done>ask(
                    replyTo -> new VersionedArtifactCommand.AddAssets(
                        update.collection().coordinates(), update.artifacts(), replyTo),
                    Duration.ofMinutes(1)
                )
                .toCompletableFuture()
                .join()
        );
    }
}
