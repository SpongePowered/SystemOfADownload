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
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.japi.Pair;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorSink;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Either;
import io.vavr.control.Try;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Function;

public final class RepositoryCloner {

    sealed interface CloneCommand extends Jsonable {
    }

    sealed interface CloneResponse extends Jsonable {
    }

    record CloneRepos(
        ArtifactCoordinates coordinates,
        List<URI> urls,
        ActorRef<CloneResponse> replyTo
    ) implements CloneCommand {
    }

    public static Behavior<CloneCommand> cloner() {
        return Behaviors.setup(ctx -> {
            final var materializer = Materializer.createMaterializer(ctx);
            return waiting(materializer);
        });
    }

    private static Behavior<CloneCommand> waiting(Materializer materializer) {
        return Behaviors.setup(ctx -> Behaviors.receive(CloneCommand.class)
            .onMessage(CloneRepos.class, msg -> {
                callClone(ctx, materializer, msg);
                return cloning(new CloneState(msg, msg.coordinates), materializer, List.empty());
            })
            .build());
    }

    record SuccessfullyCloned(
        ArtifactCoordinates coordinates,
        Map<URI, Path> checkedOut
    ) implements CloneResponse {
    }

    record PartialClone(
        ArtifactCoordinates coordinates,
        Map<URI, Path> checkedOut
    ) implements CloneResponse {
    }

    private record CloneState(
        CloneRepos command,
        ArtifactCoordinates coordinates,
        Set<URI> completed,
        Map<URI, Path> checkedOut,
        Set<URI> failed
    ) {

        public CloneState(CloneRepos msg, ArtifactCoordinates coordinates) {
            this(msg, coordinates, HashSet.empty(), HashMap.empty(), HashSet.empty());
        }

        CloneState withFailed(URI uri) {
            if (this.completed.contains(uri)) {
                return this;
            }
            return new CloneState(
                this.command,
                this.coordinates,
                this.completed.add(uri),
                this.checkedOut,
                this.failed.add(uri)
            );
        }

        CloneState withSucceeded(URI repo, Path path) {
            if (this.completed.contains(repo)) {
                return this;
            }
            return new CloneState(
                this.command,
                this.coordinates,
                this.completed.add(repo),
                this.checkedOut.put(repo, path),
                this.failed
            );
        }
    }


    private static Behavior<CloneCommand> cloning(CloneState state, Materializer materializer, List<CloneRepos> queue) {
        return Behaviors.setup(ctx -> Behaviors.receive(CloneCommand.class)
            .onMessage(CloneRepos.class, msg -> cloning(state, materializer, queue.append(msg)))
            .onMessage(CloneFailed.class, msg -> {
                ctx.getLog().warn(String.format("[%s] Clone failed for %s", msg.coordinates, msg.repo), msg.cause);
                final var newState = state.withFailed(msg.repo);
                return cloning(newState, materializer, queue);
            })
            .onMessage(CloneSucceeded.class, msg -> {
                final var newState = state.withSucceeded(msg.repo, msg.path);
                if (ctx.getLog().isDebugEnabled()) {
                    ctx.getLog().debug("Clone Succeeded for {}", msg.repo);
                }
                return cloning(newState, materializer, queue);
            })
            .onMessage(CloneFailedCompletion.class, msg -> {
                ctx.getLog().warn("[{}] Clone failed for {}", msg.coordinates, msg.cause);
                state.command.replyTo.tell(new PartialClone(state.coordinates, state.checkedOut));
                return cloneNextOrWait(materializer, queue, ctx);
            })
            .onMessage(CloneCompleted.class, msg -> {
                ctx.getLog().info("Clone completed for {}", state.coordinates);
                state.command.replyTo.tell(new SuccessfullyCloned(state.coordinates, state.checkedOut));
                return cloneNextOrWait(materializer, queue, ctx);
            })
            .build());
    }

    private static Behavior<CloneCommand> cloneNextOrWait(
        final Materializer materializer, final List<CloneRepos> queue,
        final ActorContext<CloneCommand> ctx
    ) {
        if (queue.isEmpty()) {
            return waiting(materializer);
        }
        final var head = queue.head();
        final var nextState = new CloneState(
            head,
            head.coordinates
        );
        callClone(ctx, materializer, head);
        return cloning(nextState, materializer, queue.tail());
    }

    private static void callClone(ActorContext<CloneCommand> ctx, Materializer materializer, CloneRepos head) {
        final Source<URI, NotUsed> urls = Source.from(head.urls);
        final var log = ctx.getLog();
        final Flow<URI, CloneCommand, NotUsed> flow = Flow.fromFunction(url -> cloneRepo(log, head.coordinates, url)
            .<CloneCommand>map(path -> {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Cloned {} to {}", head.coordinates.asMavenString(), url, path);
                }
                return new CloneSucceeded(head.coordinates, url, path);
            })
            .<CloneCommand>mapLeft(uri -> {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Clone failed for {}", head.coordinates.asMavenString(), uri);
                }
                return new CloneFailed(head.coordinates, uri.first(), uri.second());
            })
            .fold(Function.identity(), Function.identity())
        );
        final Sink<CloneCommand, NotUsed> sink = ActorSink.actorRef(
            ctx.getSelf(), new CloneCompleted(head.coordinates),
            t -> new CloneFailedCompletion(head.coordinates, t)
        );
        urls
            .via(flow)
            .to(sink)
            .run(materializer);
    }


    private record CloneFailed(
        ArtifactCoordinates coordinates,
        URI repo,
        Throwable cause
    ) implements CloneCommand {
    }

    private record CloneFailedCompletion(
        ArtifactCoordinates coordinates,
        Throwable cause
    ) implements CloneCommand {

    }

    private record CloneSucceeded(
        ArtifactCoordinates coordinates,
        URI repo,
        Path path
    ) implements CloneCommand {
    }

    private record CloneCompleted(ArtifactCoordinates coordinates) implements CloneCommand {
    }

    private static Either<Pair<URI, Throwable>, Path> cloneRepo(
        final Logger log,
        final ArtifactCoordinates coordinates,
        final URI remoteRepo
    ) {
        final var tempdirPrefix = String.format(
            "soad-%s-%s", coordinates.artifactId,
            UUID.randomUUID()
        );
        final var repoDirectory = Try.of(() -> Files.createTempDirectory(
            tempdirPrefix
        ));

        if (log.isTraceEnabled()) {
            log.trace("Preparing directory for checkout {}", repoDirectory);
        }
        final var writer = new ActorLoggerPrinterWriter(log);
        final var cloneCommand = repoDirectory.mapTry(Path::toFile)
            .map(directory -> Git.cloneRepository()
                .setDirectory(directory)
                .setProgressMonitor(writer)
                .setCloneAllBranches(false)
                .setCloneSubmodules(true)
                .setURI(remoteRepo.toString())
            );
        if (log.isTraceEnabled()) {
            log.trace("Checking out {} to {}", remoteRepo, repoDirectory);
        }
        return cloneCommand.mapTry(org.eclipse.jgit.api.CloneCommand::call)
            .flatMapTry(repo -> repoDirectory)
            .toEither()
            .mapLeft(t -> Pair.create(remoteRepo, t));
    }

}
