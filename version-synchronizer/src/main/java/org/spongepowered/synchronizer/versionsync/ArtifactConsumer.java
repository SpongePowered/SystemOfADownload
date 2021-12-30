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
package org.spongepowered.synchronizer.versionsync;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Routers;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.stream.javadsl.Flow;
import akka.stream.typed.javadsl.ActorFlow;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.event.GroupUpdate;
import org.spongepowered.downloads.auth.api.utils.AuthUtils;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.synchronizer.SonatypeSynchronizer;
import org.spongepowered.synchronizer.SynchronizerSettings;
import org.spongepowered.synchronizer.actor.ArtifactSyncWorker;

public final class ArtifactConsumer {
    public static void subscribeToArtifactUpdates(
        final ActorContext<SonatypeSynchronizer.Command> context,
        final ArtifactService artifactService,
        final VersionsService versionsService,
        final ClusterSharding clusterSharding,
        final SynchronizerSettings settings
    ) {
        // region Synchronize Artifact Versions from Maven
        final AuthUtils auth = AuthUtils.configure(context.getSystem().settings().config());
        ArtifactVersionSyncModule.setup(context, clusterSharding, auth, versionsService);

        final var syncWorkerBehavior = ArtifactSyncWorker.create(clusterSharding);
        final var pool = Routers.pool(
            settings.reactiveSync.poolSize,
            syncWorkerBehavior
        );

        final var registrationRef = context.spawn(
            pool,
            "group-event-subscriber",
            DispatcherSelector.defaultDispatcher()
        );
        final Flow<GroupUpdate, Done, NotUsed> actorAsk = ActorFlow.ask(
            settings.reactiveSync.parallelism,
            registrationRef, settings.reactiveSync.timeOut,
            (g, b) -> {
                if (!(g instanceof GroupUpdate.ArtifactRegistered a)) {
                    return new ArtifactSyncWorker.Ignored(b);
                }
                return new ArtifactSyncWorker.PerformResync(a.coordinates(), b);
            }
        );
        artifactService.groupTopic()
            .subscribe()
            .atLeastOnce(actorAsk);
        // endregion
    }
}
