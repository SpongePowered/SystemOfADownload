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
package org.spongepowered.downloads.versions.util.jgit;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.ServiceKey;
import akka.japi.function.Function;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.VersionedCommit;
import org.spongepowered.downloads.versions.worker.consumer.CommitDetailsRegistrar;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * An {@link akka.actor.Actor} that accepts a Versioned Artifact with a commit
 * sha to resolve the
 * underlying Git Commit information (like the commit message, author, etc.) as
 * the sha's are being resolved.
 */
public final class CommitResolver {

    public static final ServiceKey<Command> SERVICE_KEY = ServiceKey.create(Command.class, "commit-resolver");

    public sealed interface Command {
    }

    public static record ResolveCommitDetails(
        MavenCoordinates coordinates,
        String commit,
        List<URI> gitRepo,
        ActorRef<Done> replyTo
    ) implements Command {
    }

    private static record CommitDetailsRegistered(ActorRef<Done> replyTo) implements Command {

    }

    private static record AppendFileForDeletion(Path path) implements Command {
    }

    public static Behavior<Command> resolveCommit(ActorRef<CommitDetailsRegistrar.Command> registrar) {
        return Behaviors.setup(ctx -> {
            final var filesToDelete = new LinkedList<Path>();
            return Behaviors.receive(Command.class)
                .onMessage(ResolveCommitDetails.class, checkoutGitRepo(ctx, registrar))
                .onMessage(AppendFileForDeletion.class, msg -> {
                    filesToDelete.add(msg.path);
                    return Behaviors.same();
                })
                .onMessage(CommitDetailsRegistered.class, msg -> {
                    msg.replyTo.tell(Done.done());
                    return Behaviors.same();
                })
                .onSignal(PostStop.class, signal -> {
                    filesToDelete.forEach(path -> Try.ofCallable(() -> {
                        if (Files.exists(path)) {
                            Files.delete(path);
                        }
                        return path;
                    }).get());
                    return Behaviors.stopped();
                })
                .build();
        });
    }

    private static Function<ResolveCommitDetails, Behavior<Command>> checkoutGitRepo(
        final ActorContext<Command> ctx,
        final ActorRef<CommitDetailsRegistrar.Command> registrar
    ) {
        return msg -> {
            if (ctx.getLog().isTraceEnabled()) {
                ctx.getLog().trace("Starting commit resolution with {}", msg);
            }
            final ObjectId objectId = ObjectId.fromString(msg.commit);
            final var tempdirPrefix = String.format(
                "soad-%s-%s", msg.coordinates.artifactId,
                msg.commit.substring(0, Math.min(msg.commit.length(), 6))
            );

            final var log = ctx.getLog();
            final var writer = new ActorLoggerPrinterWriter(log);

            final var repos = msg.gitRepo;
            if (repos.isEmpty()) {
                msg.replyTo.tell(Done.done());
                return Behaviors.same();
            }
            final Optional<Tuple2<URI, VersionedCommit>> details = repos.map(remoteRepo -> {
                    final Path repoDirectory;
                    try {
                        repoDirectory = Files.createTempDirectory(
                            tempdirPrefix
                        );

                        if (ctx.getLog().isTraceEnabled()) {
                            log.trace("Preparing directory for checkout {}", repoDirectory);
                        }
                        final var clone = Git.cloneRepository()
                            .setDirectory(repoDirectory.toFile())
                            .setProgressMonitor(writer)
                            .setCloneAllBranches(false)
                            .setCloneSubmodules(true)
                            .setURI(remoteRepo.toString());
                        if (ctx.getLog().isTraceEnabled()) {
                            log.trace("Checking out {} to {}", remoteRepo, repoDirectory);
                        }
                        final var git = Try.of(clone::call)
                            .flatMapTry(repo ->
                                Try.of(() -> Files.walkFileTree(
                                        repoDirectory,
                                        new FileWalkerConsumer(path -> ctx.getSelf().tell(new AppendFileForDeletion(path)))
                                    ))
                                    .map(ignored -> repo)
                            )
                            .onFailure(throwable -> {
                                try {
                                    Files.walkFileTree(
                                        repoDirectory,
                                        new FileWalkerConsumer(path -> ctx.getSelf().tell(new AppendFileForDeletion(path)))
                                    );
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });

                        final var revWalk = git.map(Git::getRepository).map(RevWalk::new);

                        final var resolved = revWalk.mapTry(walker -> {
                            final var revCommit = walker.lookupCommit(objectId);
                            walker.parseBody(revCommit);
                            if (log.isTraceEnabled()) {
                                log.trace("Commit Body Parsed {}", revCommit);
                            }
                            walker.parseHeaders(revCommit);
                            if (log.isTraceEnabled()) {
                                log.trace("Commit Headers Parsed {}", revCommit.getShortMessage());
                            }
                            final var commitTime = revCommit.getCommitTime();
                            if (log.isTraceEnabled()) {
                                log.trace("Commit Revision {}", revCommit.getCommitTime());
                            }
                            if (log.isDebugEnabled()) {
                                log.debug("{} at {} has {}", msg.coordinates, msg.commit, commitTime);
                            }
                            return revCommit;
                        });
                        final var commitExtracted = resolved.toJavaOptional();
                        final var detailsOpt = commitExtracted.map(commit -> {
                            if (ctx.getLog().isTraceEnabled()) {
                                log.trace("Commit Resolved {}", commit);
                            }
                            final var commitMessage = commit.getShortMessage();
                            final var commitBody = commit.getFullMessage();
                            final var commitSha = commit.getId().getName();
                            final var commitLink = URI.create(remoteRepo.toString()
                                .replace("git@", "https://")
                                .replace(".git", "/commit/" + commitSha));
                            final var commitDate = commit.getCommitTime();
                            final var instant = Instant.ofEpochSecond(commitDate);
                            final var committerIdent = commit.getCommitterIdent();
                            final var committer = new VersionedCommit.Commiter(
                                committerIdent.getName(), committerIdent.getEmailAddress());
                            final var authorIdent = commit.getAuthorIdent();
                            final var author = new VersionedCommit.Author(
                                authorIdent.getName(), authorIdent.getEmailAddress());
                            final var timeZone = committerIdent
                                .getTimeZone();
                            final var commitDateTime = instant.atZone(timeZone.toZoneId());
                            return new VersionedCommit(
                                commitMessage,
                                commitBody,
                                commitSha,
                                author,
                                committer,
                                Optional.of(commitLink),
                                commitDateTime
                            );
                        });
                        Files.walkFileTree(repoDirectory, new FileWalkerConsumer(Files::delete));

                        return detailsOpt.map(c -> Tuple.of(remoteRepo, c));
                    } catch (IOException e) {
                        return Optional.<Tuple2<URI, VersionedCommit>>empty();
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .find(derp -> true)
                .toJavaOptional();

            details.ifPresent(d -> {
                ctx.getLog().info("[{}] Commit resolved: {}", msg.coordinates, d._2);
            });
            final var future = details.map(d ->
                AskPattern.<CommitDetailsRegistrar.Command, Done>ask(
                    registrar,
                    replyTo -> new CommitDetailsRegistrar.HandleVersionedCommitReport(d._1, d._2, msg.coordinates, replyTo),
                    Duration.ofSeconds(40),
                    ctx.getSystem().scheduler()
                )
            )
                .orElseGet(() -> CompletableFuture.completedFuture(Done.done()));

            ctx.pipeToSelf(
                future,
                (done, throwable) -> {
                    if (throwable != null) {
                        ctx.getLog().warn("Failed to register commit details", throwable);
                    }
                    return new CommitDetailsRegistered(msg.replyTo);
                }
            );
            return Behaviors.same();
        };
    }

}
