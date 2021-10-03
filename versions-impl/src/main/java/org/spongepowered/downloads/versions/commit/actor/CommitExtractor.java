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
package org.spongepowered.downloads.versions.commit.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import io.vavr.Tuple;
import io.vavr.control.Try;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.spongepowered.downloads.versions.commit.CommitSha;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.util.Optional;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

final class CommitExtractor {

    interface ChildCommand {
    }

    public final record AttemptFileCommit(
        VersionedAssetWorker.PotentiallyUsableAsset asset,
        String gitRepo,
        ActorRef<AssetCommitResponse> ref
    ) implements ChildCommand {
    }

    interface AssetCommitResponse {
    }

    public final record DiscoveredCommitFromFile(CommitSha sha, VersionedAssetWorker.PotentiallyUsableAsset asset)
        implements AssetCommitResponse {
    }

    public final record FailedToRetrieveCommit(VersionedAssetWorker.PotentiallyUsableAsset asset)
        implements AssetCommitResponse {
    }


    static Behavior<ChildCommand> extractCommitFromAssets() {
        return Behaviors.setup(ctx -> Behaviors.receive(ChildCommand.class)
            .onMessage(AttemptFileCommit.class, cmd -> {
                final var downloadURL = Try.of(cmd.asset.downloadURL()::toURL);
                final var requestAndFile = downloadURL.mapTry(req -> {
                    final var tempFile = Files.createTempFile(
                        String.format(
                            "commit-check-%s",
                            cmd.asset.coordinates()
                        ),
                        ".jar"
                    );
                    return Tuple.of(req, tempFile);
                });

                final var future = requestAndFile
                    .flatMapTry(tuple ->
                        Try.withResources(
                                () -> Channels.newChannel(tuple._1.openStream()),
                                () -> new FileOutputStream(tuple._2.toString()).getChannel()
                            )
                            .of((remoteFile, transfer) -> {
                                transfer.transferFrom(remoteFile, 0, Long.MAX_VALUE);
                                return tuple._2;
                            })
                            .flatMapTry(
                                path -> Try.withResources(() -> new JarInputStream(Files.newInputStream(path)))
                                    .of(JarInputStream::getManifest)
                                    .map(Manifest::getMainAttributes)
                                    .map(attributes -> attributes.getValue("Git-Commit"))
                                    .map(Optional::ofNullable)
                                    .flatMap(opt -> opt.map(sha -> Try.of(() -> ObjectId.fromString(sha)))
                                        .orElseGet(() -> Try.failure(new IllegalStateException(
                                            String.format(
                                                "Could not process artifact commit %s",
                                                cmd.asset.coordinates()
                                            )))))
                                    .map(CommitExtractor::fromObjectId)
                            )
                    )
                    .toCompletableFuture();
                ctx.pipeToSelf(future, (sha, throwable) -> {
                    if (throwable != null) {
                        return new FailedFile(throwable, cmd.asset, cmd.ref);
                    }
                    return new CommitRetrievedFromFile(sha, cmd.asset, cmd.ref);
                });
                return Behaviors.same();
            })
            .onMessage(FailedFile.class, cmd -> {
                cmd.replyTo.tell(new FailedToRetrieveCommit(cmd.asset));
                return Behaviors.same();
            })
            .onMessage(CommitRetrievedFromFile.class, cmd -> {
                cmd.replyTo.tell(new DiscoveredCommitFromFile(cmd.sha, cmd.asset));
                return Behaviors.same();
            })
            .build());
    }

    static CommitSha fromObjectId(final ObjectId oid) {
        // Seriously... why can't they just give us the damn 5 ints....
        final var bytes = new byte[Constants.OBJECT_ID_LENGTH];
        oid.copyTo(bytes, 0);

        final IntBuffer intBuf = ByteBuffer.wrap(bytes)
            .order(ByteOrder.BIG_ENDIAN)
            .asIntBuffer();
        final int[] array = new int[intBuf.remaining()];
        intBuf.get(array);
        final long shaPart1 = (long) array[0] << 16 & array[1];
        final long shaPart2 = (long) array[2] << 16 & array[3];
        return new CommitSha(shaPart1, shaPart2, array[4]);
    }

    private final record FailedFile(
        Throwable throwable,
        VersionedAssetWorker.PotentiallyUsableAsset asset,
        ActorRef<AssetCommitResponse> replyTo
    ) implements ChildCommand {
    }

    private final record CommitRetrievedFromFile(
        CommitSha sha,
        VersionedAssetWorker.PotentiallyUsableAsset asset,
        ActorRef<AssetCommitResponse> replyTo
    ) implements ChildCommand {
    }

}
