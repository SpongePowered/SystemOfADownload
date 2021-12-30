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

import akka.actor.typed.ActorSystem;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Routers;
import akka.actor.typed.receptionist.Receptionist;
import akka.cluster.Member;
import org.spongepowered.downloads.versions.worker.actor.artifacts.CommitExtractor;
import org.spongepowered.downloads.versions.worker.actor.artifacts.FileCollectionOperator;
import org.spongepowered.downloads.versions.worker.actor.delegates.RawCommitReceiver;

import java.util.UUID;

public final class WorkerSpawner {

    public static void spawnWorkers(ActorSystem<Void> system, Member member, ActorContext<Void> ctx) {
        // Set up the usual actors
        final var versionConfig = VersionExtension.Settings.get(system);
        final var poolSizePerInstance = versionConfig.commitFetch.poolSize;

        if (member.hasRole("file-extractor")) {
            final var commitFetcherUID = UUID.randomUUID();
            final var behavior = CommitExtractor.extractCommitFromAssets();
            final var assetRefresher = Behaviors.supervise(behavior)
                .onFailure(SupervisorStrategy.resume());
            final var pool = Routers.pool(poolSizePerInstance, assetRefresher);

            final var commitExtractorRef = ctx.spawn(
                pool,
                "file-commit-worker-" + commitFetcherUID,
                DispatcherSelector.defaultDispatcher()
            );
            // Announce it to the cluster
            ctx.getSystem().receptionist().tell(Receptionist.register(CommitExtractor.SERVICE_KEY, commitExtractorRef));

            final var receiver = Behaviors.supervise(RawCommitReceiver.receive())
                .onFailure(SupervisorStrategy.resume());
            final var receiverRef = ctx.spawn(receiver, "file-scan-result-receiver-" + commitFetcherUID);
            final var jarScanner = FileCollectionOperator.scanJarFilesForCommit(commitExtractorRef, receiverRef);
            final var supervisedScanner = Behaviors.supervise(jarScanner).onFailure(SupervisorStrategy.resume());
            final var scannerPool = Routers.pool(poolSizePerInstance, supervisedScanner);
            final var scannerRef = ctx.spawn(scannerPool, "file-collection-worker-" + commitFetcherUID);
            ctx.getSystem().receptionist().tell(Receptionist.register(FileCollectionOperator.KEY, scannerRef));
        }
    }


}
