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
package org.spongepowered.downloads.versions.worker.actor.artifacts;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.ServiceKey;
import akka.japi.function.Function2;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.eclipse.jgit.lib.ObjectId;

import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

/**
 * Actor that extracts the commit from a jar file.
 */
public final class CommitExtractor {

    public static final ServiceKey<ChildCommand> SERVICE_KEY = ServiceKey.create(
        ChildCommand.class, "commit-extractor");
    private static final FileAttribute<java.util.Set<PosixFilePermission>> OWNER_READ_WRITE_ATTRIBUTE = PosixFilePermissions.asFileAttribute(
        EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));

    public sealed interface ChildCommand {
    }

    public final record AttemptFileCommit(
        PotentiallyUsableAsset asset,
        ActorRef<AssetCommitResponse> ref
    ) implements ChildCommand {
    }

    private final record NoCommitsFound(PotentiallyUsableAsset asset, ActorRef<AssetCommitResponse> ref) implements ChildCommand {
    }

    private final record FailedFile(
        Throwable throwable,
        PotentiallyUsableAsset asset,
        ActorRef<AssetCommitResponse> replyTo
    ) implements ChildCommand {
    }

    private final record CommitRetrievedFromFile(
        String sha,
        PotentiallyUsableAsset asset,
        ActorRef<AssetCommitResponse> replyTo
    ) implements ChildCommand {
    }

    public sealed interface AssetCommitResponse {
    }

    public final record DiscoveredCommitFromFile(String sha, PotentiallyUsableAsset asset)
        implements AssetCommitResponse {
    }


    public final record NoCommitsFoundForFile(PotentiallyUsableAsset asset) implements AssetCommitResponse {
    }

    public final record FailedToRetrieveCommit(PotentiallyUsableAsset asset)
        implements AssetCommitResponse {
    }

    public static Behavior<ChildCommand> extractCommitFromAssets() {
        return Behaviors.setup(ctx -> Behaviors.receive(ChildCommand.class)
            .onMessage(AttemptFileCommit.class, cmd -> {
                if (ctx.getLog().isTraceEnabled()) {
                    ctx.getLog().trace("Attempting file commit extraction from {}", cmd.asset);
                }
                ctx.pipeToSelf(fetchCommitIdFromFile(cmd), handleFileExtractionResult(ctx, cmd));
                return working(cmd.asset, List.empty());
            })
            .build());
    }

    private static Function2<Optional<ObjectId>, Throwable, ChildCommand> handleFileExtractionResult(
        ActorContext<ChildCommand> ctx, AttemptFileCommit cmd
    ) {
        return (sha, throwable) -> {
            if (throwable != null) {
                ctx.getLog().info("Marking file {} as failed", cmd.asset);
                return new FailedFile(throwable, cmd.asset, cmd.ref);
            }
            if (sha.isEmpty()) {
                if (ctx.getLog().isDebugEnabled()) {
                    ctx.getLog().info("File {} doesn't have a commit", cmd.asset);
                }
                return new NoCommitsFound(cmd.asset, cmd.ref);
            }
            final var commit = sha.get();
            if (ctx.getLog().isDebugEnabled()) {
                ctx.getLog().debug(
                    "[{}] Commit extracted from jar {}", cmd.asset.mavenCoordinates().asStandardCoordinates(),
                    commit.name()
                );
            }
            return new CommitRetrievedFromFile(commit.name(), cmd.asset, cmd.ref);
        };
    }


    private static Behavior<ChildCommand> working(
        final PotentiallyUsableAsset working,
        final List<AttemptFileCommit> queue
    ) {
        return Behaviors.setup(ctx -> Behaviors.receive(ChildCommand.class)
            .onMessage(AttemptFileCommit.class, cmd -> {
                if (ctx.getLog().isTraceEnabled()) {
                    ctx.getLog().trace("Received additional work while processing {}", working.mavenCoordinates());
                }
                return working(working, queue.append(cmd));
            })
            .onMessage(FailedFile.class, cmd -> {
                if (ctx.getLog().isTraceEnabled()) {
                    ctx.getLog().trace("Failed commit extraction for " + cmd.asset.mavenCoordinates(), cmd.throwable);
                }
                cmd.replyTo.tell(new FailedToRetrieveCommit(cmd.asset));
                return swapToNext(ctx, queue);
            })
            .onMessage(NoCommitsFound.class, cmd -> {
                if (ctx.getLog().isTraceEnabled()) {
                    ctx.getLog().trace("No commits found for " + working.mavenCoordinates());
                }
                cmd.ref.tell(new NoCommitsFoundForFile(cmd.asset));
                return swapToNext(ctx, queue);
            })
            .onMessage(CommitRetrievedFromFile.class, cmd -> {
                if (ctx.getLog().isTraceEnabled()) {
                    ctx.getLog().trace(
                        "Commit extracted from {} for {}", cmd.asset.coordinates(), working.mavenCoordinates());
                }
                cmd.replyTo.tell(new DiscoveredCommitFromFile(cmd.sha, cmd.asset));
                return swapToNext(ctx, queue);
            })
            .build());
    }

    private static Behavior<ChildCommand> swapToNext(
        final ActorContext<ChildCommand> ctx,
        final List<AttemptFileCommit> queue
    ) {
        if (queue.isEmpty()) {
            return extractCommitFromAssets();
        }
        final var next = queue.head();
        ctx.pipeToSelf(fetchCommitIdFromFile(next), handleFileExtractionResult(ctx, next));
        return working(next.asset, queue.tail());
    }

    private static CompletableFuture<Optional<ObjectId>> fetchCommitIdFromFile(
        AttemptFileCommit cmd
    ) {
        return Try.of(cmd.asset.downloadURL()::toURL)
            .mapTry(req -> {
                final var tempFile = Files.createTempFile(
                    String.format(
                        "commit-check-%s",
                        cmd.asset.coordinates()
                    ),
                    ".jar",
                    OWNER_READ_WRITE_ATTRIBUTE
                );
                return Tuple.of(req, tempFile);
            })
            .flatMap(CommitExtractor::getCommitFromFile)
            .mapTry(CommitExtractor::extractCommitFromJarManifest)
            .toCompletableFuture();
    }

    private static Try<Path> getCommitFromFile(Tuple2<URL, Path> tuple) {
        return Try.withResources(
                () -> Channels.newChannel(tuple._1.openStream()),
                () -> FileChannel.open(tuple._2, StandardOpenOption.WRITE)
            )
            .of((remoteFile, transfer) -> {
                transfer.transferFrom(remoteFile, 0, Long.MAX_VALUE);
                return tuple._2;
            });
    }

    private static Optional<ObjectId> extractCommitFromJarManifest(Path path) {
        return Try.withResources(
                () -> new JarInputStream(Files.newInputStream(path, StandardOpenOption.DELETE_ON_CLOSE)))
            .of(JarInputStream::getManifest)
            .filter(Objects::nonNull)
            .mapTry(Manifest::getMainAttributes)
            .mapTry(attributes -> attributes.getValue("Git-Commit"))
            .map(Optional::ofNullable)
            .map(opt -> opt.flatMap(sha -> Try.of(() -> ObjectId.fromString(sha))
                .map(Optional::of)
                .getOrElse(Optional::empty)
            ))
            .getOrElse(Optional::empty);
    }

}
