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
package org.spongepowered.downloads.versions.worker.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.ServiceKey;
import io.vavr.Tuple;
import io.vavr.control.Try;
import org.eclipse.jgit.lib.ObjectId;

import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

public final class CommitExtractor {

    public static final ServiceKey<ChildCommand> SERVICE_KEY = ServiceKey.create(ChildCommand.class, "commit-extractor");
    private static final FileAttribute<Set<PosixFilePermission>> OWNER_READ_WRITE_ATTRIBUTE = PosixFilePermissions.asFileAttribute(
        EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));

    public interface ChildCommand {
    }

    public final record AttemptFileCommit(
        VersionedAssetWorker.PotentiallyUsableAsset asset,
        String gitRepo,
        ActorRef<AssetCommitResponse> ref
    ) implements ChildCommand {
    }

    public interface AssetCommitResponse {
    }

    public final record DiscoveredCommitFromFile(String sha, VersionedAssetWorker.PotentiallyUsableAsset asset)
        implements AssetCommitResponse {
    }

    public final record FailedToRetrieveCommit(VersionedAssetWorker.PotentiallyUsableAsset asset)
        implements AssetCommitResponse {
    }


    public static Behavior<ChildCommand> extractCommitFromAssets() {
        return Behaviors.setup(ctx -> Behaviors.receive(ChildCommand.class)
            .onMessage(AttemptFileCommit.class, cmd -> {
                final var downloadURL = Try.of(cmd.asset.downloadURL()::toURL);
                final var requestAndFile = downloadURL.mapTry(req -> {
                    final var tempFile = Files.createTempFile(
                        String.format(
                            "commit-check-%s",
                            cmd.asset.coordinates()
                        ),
                        ".jar",
                        OWNER_READ_WRITE_ATTRIBUTE
                    );
                    return Tuple.of(req, tempFile);
                });

                final var future = requestAndFile
                    .flatMapTry(tuple ->
                        Try.withResources(
                                () -> Channels.newChannel(tuple._1.openStream()),
                                () -> FileChannel.open(tuple._2, StandardOpenOption.WRITE)
                            )
                            .of((remoteFile, transfer) -> {
                                transfer.transferFrom(remoteFile, 0, Long.MAX_VALUE);
                                return tuple._2;
                            })
                            .flatMapTry(
                                path -> Try.withResources(() -> new JarInputStream(Files.newInputStream(path, StandardOpenOption.DELETE_ON_CLOSE)))
                                    .of(JarInputStream::getManifest)
                                    .mapTry(Manifest::getMainAttributes)
                                    .mapTry(attributes -> attributes.getValue("Git-Commit"))
                                    .map(Optional::ofNullable)
                                    .flatMap(opt -> opt.map(sha -> Try.of(() -> ObjectId.fromString(sha)))
                                        .orElseGet(() -> Try.failure(new IllegalStateException(
                                            String.format(
                                                "Could not process artifact commit %s",
                                                cmd.asset.coordinates()
                                            )))))
                            )
                    )
                    .toCompletableFuture();
                ctx.pipeToSelf(future, (sha, throwable) -> {
                    if (throwable != null) {
                        return new FailedFile(throwable, cmd.asset, cmd.ref);
                    }
                    return new CommitRetrievedFromFile(sha.name(), cmd.asset, cmd.ref);
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

    private final record FailedFile(
        Throwable throwable,
        VersionedAssetWorker.PotentiallyUsableAsset asset,
        ActorRef<AssetCommitResponse> replyTo
    ) implements ChildCommand {
    }

    private final record CommitRetrievedFromFile(
        String sha,
        VersionedAssetWorker.PotentiallyUsableAsset asset,
        ActorRef<AssetCommitResponse> replyTo
    ) implements ChildCommand {
    }

}
