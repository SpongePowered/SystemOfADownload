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

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.Member;
import org.spongepowered.downloads.util.akka.ClusterUtil;
import org.spongepowered.downloads.versions.api.delegates.CommitDetailsRegistrar;
import org.spongepowered.downloads.versions.worker.actor.artifacts.CommitExtractor;
import org.spongepowered.downloads.versions.worker.actor.artifacts.FileCollectionOperator;
import org.spongepowered.downloads.versions.worker.actor.delegates.InternalCommitRegistrar;
import org.spongepowered.downloads.versions.worker.actor.delegates.RawCommitReceiver;

import java.util.UUID;

public final class WorkerSpawner {

    public static void spawnWorkers(ActorSystem<Void> system, Member member, ActorContext<Void> ctx) {
        // Set up the usual actors
        final var versionConfig = VersionExtension.Settings.get(system);
        final var poolSizePerInstance = versionConfig.commitFetch.poolSize;


        for (int i = 0; i < poolSizePerInstance; i++) {
            final var commitFetcherUID = UUID.randomUUID();
            if (member.hasRole("file-extractor")) {
                final ActorRef<CommitExtractor.ChildCommand> commitExtractor = ClusterUtil.spawnRemotableWorker(
                    ctx,
                    CommitExtractor::extractCommitFromAssets,
                    () -> CommitExtractor.SERVICE_KEY,
                    () -> "file-commit-worker-" + commitFetcherUID
                );
                final var uid = UUID.randomUUID();
                final var receiver = Behaviors.supervise(RawCommitReceiver.receive())
                    .onFailure(SupervisorStrategy.resume());
                final var receiverRef = ctx.spawn(receiver, "file-scan-result-receiver-" + uid);
                ClusterUtil.spawnRemotableWorker(
                    ctx,
                    () -> FileCollectionOperator.scanJarFilesForCommit(commitExtractor, receiverRef),
                    () -> FileCollectionOperator.KEY,
                    () -> "file-collection-worker-" + commitFetcherUID
                );
            }

            ClusterUtil.spawnRemotableWorker(
                ctx,
                InternalCommitRegistrar::register,
                () -> CommitDetailsRegistrar.SERVICE_KEY,
                () -> "internal-commit-registrar" + commitFetcherUID
            );
        }

    }


}
