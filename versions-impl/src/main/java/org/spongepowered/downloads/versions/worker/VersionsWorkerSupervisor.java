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
package org.spongepowered.downloads.versions.worker;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.Cluster;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.worker.consumer.ArtifactSubscriber;
import org.spongepowered.downloads.versions.worker.consumer.VersionedAssetSubscriber;

/**
 * The "reactive" side of Versions where it performs all the various tasks
 * involved with managing Artifact Versions and their artifact assets with
 * relation to those versions. Crucially, this performs the various sync jobs
 * required to derive a Version from an artifact, as well as the various
 * metadata with that version, such as assets and the commit information for
 * that asset. This is mostly a guardian actor, one that wires up children
 * actors to perform the actual work against topic subscribers, either from
 * {@link ArtifactService#artifactUpdate()} or
 * {@link VersionsService#artifactUpdateTopic()}.
 * <p>The important reasoning why this is split out from the Version Service
 * implementation is that this particular supervisor may well be able to handle
 * updates while the VersionsService implementation is the "organizer" of
 * root information.
 */
public final class VersionsWorkerSupervisor {

    public static Behavior<Void> bootstrapWorkers(
        final ArtifactService artifacts,
        final VersionsService versions
    ) {
        return Behaviors.setup(ctx -> {
            final ClusterSharding sharding = ClusterSharding.get(ctx.getSystem());
            // Persistent EventBased Actors
            EntityStore.setupPersistedEntities(sharding);

            // Kakfa subscribers
            ArtifactSubscriber.setup(artifacts, ctx);
            VersionedAssetSubscriber.setup(ctx, versions, sharding);

            // Workers available to do most jobs
            final var member = Cluster.get(ctx.getSystem()).selfMember();
            final var system = ctx.getSystem();
            WorkerSpawner.spawnWorkers(system, member, ctx);

            // Finally, self, the supervisor
            return Behaviors.receive(Void.class)
                .build();
        });

    }

}
