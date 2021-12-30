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
package org.spongepowered.synchronizer.assetsync;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Routers;
import akka.stream.javadsl.Flow;
import akka.stream.typed.javadsl.ActorFlow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.api.models.ArtifactUpdate;
import org.spongepowered.synchronizer.SonatypeSynchronizer;
import org.spongepowered.synchronizer.SynchronizerSettings;

public class VersionConsumer {
    public static void subscribeToVersionedArtifactUpdates(
        final VersionsService versionsService, final ObjectMapper mapper,
        final ActorContext<SonatypeSynchronizer.Command> context,
        final SynchronizerSettings settings
    ) {
        // region Synchronize Versioned Assets through Sonatype Search
        final var componentPool = Routers.pool(
            settings.asset.poolSize,
            Behaviors.supervise(VersionedComponentWorker.gatherComponents(versionsService, mapper))
                .onFailure(settings.asset.backoff)
        );
        final var componentRef = context.spawn(
            componentPool,
            "version-component-registration",
            DispatcherSelector.defaultDispatcher()
        );
        final Flow<ArtifactUpdate, Done, NotUsed> versionedFlow = ActorFlow.ask(
            settings.asset.parallelism,
            componentRef,
            settings.asset.timeout,
            (g, b) -> {
                if (!(g instanceof ArtifactUpdate.ArtifactVersionRegistered a)) {
                    return new VersionedComponentWorker.Ignored(b);
                }
                return new VersionedComponentWorker.GatherComponentsForArtifact(a.coordinates(), b);
            }
        );
        versionsService.artifactUpdateTopic()
            .subscribe()
            .atLeastOnce(versionedFlow);
        // endregion
    }
}
