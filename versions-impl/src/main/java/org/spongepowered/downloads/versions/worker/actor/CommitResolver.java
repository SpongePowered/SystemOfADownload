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
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.ServiceKey;
import akka.japi.function.Function;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.VersionedCommit;
import org.spongepowered.downloads.versions.worker.akka.ActorLoggerPrinterWriter;
import org.spongepowered.downloads.versions.worker.jgit.FileWalkerConsumer;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedList;
import java.util.Optional;

/**
 * An {@link akka.actor.Actor} that accepts a Versioned Artifact with a commit
 * sha to resolve the
 * underlying Git Commit information (like the commit message, author, etc.) as
 * the sha's are being resolved.
 */
public final class CommitResolver {

    public static final ServiceKey<Command> SERVICE_KEY = ServiceKey.create(Command.class, "commit-resolver");

    public interface Command {
    }

    public static record ResolveCommitDetails(
        MavenCoordinates coordinates,
        String commit,
        List<URI> gitRepo,
        ActorRef<Optional<VersionedCommit>> replyTo
    ) implements Command {
    }

    private static record AppendFileForDeletion(Path path) implements Command {
    }

    public static Behavior<Command> resolveCommit() {
        return Behaviors.setup(ctx -> {
            final var filesToDelete = new LinkedList<Path>();
            return Behaviors.receive(Command.class)
                .onMessage(ResolveCommitDetails.class, checkoutGitRepo(ctx))
                .onMessage(AppendFileForDeletion.class, msg -> {
                    filesToDelete.add(msg.path);
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

    private static Function<ResolveCommitDetails, Behavior<Command>> checkoutGitRepo(ActorContext<Command> ctx) {
        return msg -> {
            final ObjectId objectId = ObjectId.fromString(msg.commit);
            final var tempdirPrefix = String.format(
                "soad-%s-%s", msg.coordinates.artifactId,
                msg.commit.substring(0, Math.min(msg.commit.length(), 6))
            );
            final var repoDirectory = Files.createTempDirectory(
                tempdirPrefix
            );
            final var log = ctx.getLog();
            log.info("Preparing directory for checkout {}", repoDirectory);
            final var writer = new ActorLoggerPrinterWriter(log);

            final var clone = Git.cloneRepository()
                .setDirectory(repoDirectory.toFile())
                .setProgressMonitor(writer)
                .setNoTags()
                .setCloneAllBranches(false)
                .setCloneSubmodules(true)
                .setURI(msg.gitRepo.toString());
            log.debug("Checking out {} to {}", msg.gitRepo, repoDirectory);
            final var git = Try.of(clone::call)
                .flatMapTry(repo ->
                    Try.of(() -> Files.walkFileTree(repoDirectory,
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
                log.trace("Commit Body Parsed {}", revCommit);
                walker.parseHeaders(revCommit);
                log.trace("Commit Headers Parsed {}", revCommit.getShortMessage());
                final var commitTime = revCommit.getCommitTime();
                log.trace("Commit Revision {}", revCommit.getCommitTime());
                log.debug("{} at {} has {}", msg.coordinates, msg.commit, commitTime);
                return revCommit;
            });
            final var commitExtracted = resolved.toJavaOptional();
            final var details = commitExtracted.map(commit -> {
                log.info("Commit Resolved {}", commit);
                final var commitMessage = commit.getShortMessage();
                final var commitBody = commit.getFullMessage();
                final var commitSha = commit.getId().getName();
                final var commitLink = URI.create(msg.gitRepo.toString()
                    .replace("git@", "https://")
                    .replace(".git", "/commit/" + commitSha));
                final var commitDate = commit.getCommitTime();
                final var instant = Instant.ofEpochSecond(commitDate);
                final var committerIdent = commit.getCommitterIdent();
                final var committer = new VersionedCommit.Commiter(committerIdent.getName(), committerIdent.getEmailAddress());
                final var authorIdent = commit.getAuthorIdent();
                final var author = new VersionedCommit.Author(authorIdent.getName(), authorIdent.getEmailAddress());
                final var timeZone = committerIdent
                    .getTimeZone();
                final var commitDateTime = instant.atZone(timeZone.toZoneId());
                return new VersionedCommit(
                    commitMessage,
                    commitBody,
                    commitSha,
                    author,
                    committer,
                    commitLink,
                    commitDateTime
                );
            });
            Files.walkFileTree(repoDirectory, new FileWalkerConsumer(Files::delete));
            msg.replyTo.tell(details);
            return Behaviors.same();
        };
    }

}
