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
package org.spongepowered.synchronizer.gitmanaged.util.jgit;

import akka.NotUsed;
import akka.actor.typed.javadsl.ActorContext;
import akka.japi.Pair;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;
import io.vavr.CheckedFunction1;
import io.vavr.Tuple3;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.SubmoduleConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.TagOpt;
import org.slf4j.Logger;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.VersionedCommit;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

public final class AssetCommitResolver {


    static Source<CommitResolutionResponse, NotUsed> startCommitResolution(
        final ActorContext<CommitResolutionManager.Command> ctx,
        final CommitResolutionManager.ArtifactRepoState newState,
        final CommitResolutionManager.ResolveCommitDetails request
    ) {
        final var log = ctx.getLog();
        final var checkedOut = newState.checkedOut();
        final var commitSha = request.commit();
        return Source.from(Collections.singleton(ObjectId.fromString(commitSha)))
            .mapConcat(objectId -> checkedOut.map(tuple -> tuple.append(objectId)))
            .async()
            .via(tryResolvingCommitFromGitDirectory(request.coordinates(), log))
            .async()
            .via(parseResponse(request));
    }

    sealed interface CommitResolutionResponse {
    }

    record CommitResolved(
        URI repo, VersionedCommit commit
    ) implements CommitResolutionResponse {
    }

    public record CommitNotFound(MavenCoordinates coordinates, List<URI> uris, String commit) implements CommitResolutionResponse {
    }


    static Flow<Optional<Pair<URI, VersionedCommit>>, CommitResolutionResponse, NotUsed> parseResponse(
        CommitResolutionManager.ResolveCommitDetails request
    ) {
        return Flow.<Optional<Pair<URI, VersionedCommit>>, CommitResolutionResponse>fromFunction(
            opt ->
                opt.<CommitResolutionResponse>map(pair -> new CommitResolved(pair.first(), pair.second()))
                    .orElseGet(() -> new CommitNotFound(request.coordinates(), request.gitRepo(), request.commit()))
        );
    }

    static Flow<Tuple3<URI, Path, ObjectId>, Optional<Pair<URI, VersionedCommit>>, NotUsed> tryResolvingCommitFromGitDirectory(
        MavenCoordinates coordinates, Logger log
    ) {
        return Flow.<Tuple3<URI, Path, ObjectId>, Optional<Pair<URI, VersionedCommit>>>fromFunction(tuple3 -> {
            if (log.isDebugEnabled()) {
                log.debug("Resolving commit {} for {}", tuple3._3.getName(), coordinates);
                log.debug("Opening Git directory {}", tuple3._2);
            }
            final var directory = tuple3._2().toFile();
            final var git = Git.open(directory);
            final var fetchCmd = git.fetch()
                .setRecurseSubmodules(SubmoduleConfig.FetchRecurseSubmodulesMode.YES)
                .setTagOpt(TagOpt.FETCH_TAGS)
                .setRemoveDeletedRefs(false);
            fetchCmd.call();

            return Try.withResources(() -> new RevWalk(git.getRepository()))
                .of(tryFetchAndResolveCommit(coordinates, log, tuple3._3))
                .map(convertCommitToVersionedCommit(log, tuple3._1))
                .map(Optional::of)
                .getOrElseGet(t -> {
                    log.error("Failed to resolve commit {} for {}", tuple3._3.getName(), coordinates);
                    return Optional.empty();
                });
        });
    }

    static CheckedFunction1<RevWalk, RevCommit> tryFetchAndResolveCommit(
        MavenCoordinates coordinates, Logger log, ObjectId objectId
    ) {
        return revWalk -> {
            final var commit = revWalk.lookupCommit(objectId);
            revWalk.parseBody(commit);
            if (log.isTraceEnabled()) {
                log.trace("Commit Body Parsed {}", commit);
            }
            revWalk.parseHeaders(commit);
            if (log.isTraceEnabled()) {
                log.trace("Commit Headers Parsed {}", commit.getShortMessage());
            }
            final var commitTime = commit.getCommitTime();
            if (log.isTraceEnabled()) {
                log.trace("Commit Revision {}", commit.getCommitTime());
            }
            if (log.isDebugEnabled()) {
                log.debug(
                    "{} at {} has {}", coordinates, objectId.name(), commitTime);
            }
            return commit;
        };
    }

    static java.util.function.Function<RevCommit, Pair<URI, VersionedCommit>> convertCommitToVersionedCommit(
        Logger log, URI repo
    ) {
        return commit -> {
            if (log.isTraceEnabled()) {
                log.trace("Commit Resolved {}", commit);
            }
            final var commitMessage = commit.getShortMessage();
            final var commitBody = commit.getFullMessage();
            final var commitId = commit.getId().getName();
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
            final var commitUrl = URI.create(repo.toString() + "/commit/" + commitId);
            return Pair.apply(repo, new VersionedCommit(
                commitMessage,
                commitBody,
                commitId,
                author,
                committer,
                Optional.of(commitUrl),
                commitDateTime
            ));
        };
    }
}
