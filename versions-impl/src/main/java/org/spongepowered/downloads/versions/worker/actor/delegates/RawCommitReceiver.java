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
package org.spongepowered.downloads.versions.worker.actor.delegates;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import org.spongepowered.downloads.versions.worker.actor.artifacts.CommitExtractor;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.VersionedArtifactCommand;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.VersionedArtifactEntity;

public final class RawCommitReceiver {

    public static Behavior<CommitExtractor.AssetCommitResponse> receive() {
        return Behaviors.setup(ctx -> {
            final var sharding = ClusterSharding.get(ctx.getSystem());
            return Behaviors.receive(CommitExtractor.AssetCommitResponse.class)
                .onMessage(CommitExtractor.FailedToRetrieveCommit.class, msg -> {
                    if (ctx.getLog().isDebugEnabled()) {
                        ctx.getLog().debug(
                            "[{}] Failed to retrieve commit", msg.asset().mavenCoordinates().asStandardCoordinates());
                    }
                    sharding.entityRefFor(VersionedArtifactEntity.ENTITY_TYPE_KEY, msg.asset().mavenCoordinates().asStandardCoordinates())
                        .tell(new VersionedArtifactCommand.MarkFilesAsErrored());
                    return Behaviors.same();
                })
                .onMessage(CommitExtractor.DiscoveredCommitFromFile.class, msg -> {
                    if (ctx.getLog().isDebugEnabled()) {
                        ctx.getLog().debug(
                            "[{}] Retrieved commit", msg.asset().mavenCoordinates().asStandardCoordinates());
                    }
                    sharding.entityRefFor(VersionedArtifactEntity.ENTITY_TYPE_KEY, msg.asset().mavenCoordinates().asStandardCoordinates())
                        .tell(new VersionedArtifactCommand.RegisterRawCommit(msg.sha()));
                    return Behaviors.same();
                })
                .onMessage(CommitExtractor.NoCommitsFoundForFile.class, msg -> {
                    if (ctx.getLog().isDebugEnabled()) {
                        ctx.getLog().debug(
                            "[{}] No commit found", msg.asset().mavenCoordinates().asStandardCoordinates());
                    }
                    sharding.entityRefFor(VersionedArtifactEntity.ENTITY_TYPE_KEY, msg.asset().mavenCoordinates().asStandardCoordinates())
                        .tell(new VersionedArtifactCommand.MarkFilesAsErrored());
                    return Behaviors.same();
                })
                .build();
        });
    }
}
