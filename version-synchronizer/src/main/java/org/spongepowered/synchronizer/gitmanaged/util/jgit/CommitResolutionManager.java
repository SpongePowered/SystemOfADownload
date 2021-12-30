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

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.ServiceKey;
import akka.japi.function.Function2;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorSink;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.synchronizer.actor.CommitDetailsRegistrar;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * An {@link akka.actor.Actor} that accepts a Versioned Artifact with a commit
 * sha to resolve the
 * underlying Git Commit information (like the commit message, author, etc.) as
 * the sha's are being resolved.
 */
public final class CommitResolutionManager {

    public static final ServiceKey<Command> SERVICE_KEY = ServiceKey.create(Command.class, "commit-resolver");


    @JsonDeserialize
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(ResolveCommitDetails.class),
    })
    public sealed interface Command extends Jsonable {
    }

    @JsonTypeName("resolve-commit-details")
    public record ResolveCommitDetails(
        MavenCoordinates coordinates,
        String commit,
        List<URI> gitRepo,
        ActorRef<Done> replyTo
    ) implements Command {

        @JsonCreator
        public ResolveCommitDetails {
        }
    }

    public static Behavior<Command> resolveCommit(ActorRef<CommitDetailsRegistrar.Command> registrar) {
        return Behaviors.setup(ctx -> {
            final var clonerBehavior = Behaviors.supervise(RepositoryCloner.cloner())
                .onFailure(SupervisorStrategy.restart());
            StubSystemReader.init();
            final var uuid = UUID.randomUUID();
            final var cloner = ctx.spawn(clonerBehavior, "repository-cloner-" + uuid);
            final var materializer = Materializer.createMaterializer(ctx);
            return awaiting(cloner, registrar, materializer, ClonedRepoState.EMPTY);
        });
    }

    private static Behavior<Command> awaiting(
        final ActorRef<RepositoryCloner.CloneCommand> cloner,
        final ActorRef<CommitDetailsRegistrar.Command> registrar,
        final Materializer materializer,
        final ClonedRepoState state
    ) {
        return Behaviors.setup(ctx -> Behaviors.receive(Command.class)
            .onMessage(ResolveCommitDetails.class, msg -> {
                if (ctx.getLog().isTraceEnabled()) {
                    ctx.getLog().trace("Resolving commit details for {}", msg.coordinates);
                }
                final var repoState = state.repositoriesCloned.get(msg.coordinates.asArtifactCoordinates());
                if (repoState.isEmpty()) {
                    if (msg.gitRepo.isEmpty()) {
                        msg.replyTo.tell(Done.done());
                        return Behaviors.same();
                    }
                    if (ctx.getLog().isDebugEnabled()) {
                        ctx.getLog().debug("[{}] Cloning {}", msg.coordinates, msg.gitRepo);
                    }
                    ctx.ask(
                        RepositoryCloner.CloneResponse.class,
                        cloner,
                        Duration.ofMinutes(20),
                        replyTo -> new RepositoryCloner.CloneRepos(
                            msg.coordinates.asArtifactCoordinates(), msg.gitRepo, replyTo),
                        handleCloneResponse(ctx, msg, state)
                    );
                    return waitingForClones(cloner, registrar, materializer, state, List.empty());
                }
                final var artifactRepoState = repoState.get();
                final var unclonedRepos = artifactRepoState.repositories.filter(Predicate.not(artifactRepoState.checkedOut::containsKey));
                final var newUncloned = msg.gitRepo.filter(Predicate.not(artifactRepoState.checkedOut::containsKey));
                final var allUncloned = unclonedRepos.addAll(newUncloned);
                if (!allUncloned.isEmpty()) {
                    if (ctx.getLog().isDebugEnabled()) {
                        ctx.getLog().debug("[{}] Cloning {}", msg.coordinates.asStandardCoordinates(), allUncloned);
                    }
                    ctx.ask(
                        RepositoryCloner.CloneResponse.class,
                        cloner,
                        Duration.ofMinutes(20),
                        replyTo -> new RepositoryCloner.CloneRepos(
                            msg.coordinates.asArtifactCoordinates(), allUncloned.toList(), replyTo),
                        handleCloneResponse(ctx, msg, state)
                    );

                    return waitingForClones(cloner, registrar, materializer, state, List.empty());
                }

                startCommitResolution(materializer, ctx, msg, artifactRepoState);

                return waitingForResolution(cloner, registrar, materializer, state, List.empty());
            })
            .build()
        );
    }

    private static Behavior<Command> waitingForClones(
        final ActorRef<RepositoryCloner.CloneCommand> cloner,
        final ActorRef<CommitDetailsRegistrar.Command> registrar,
        final Materializer materializer,
        final ClonedRepoState state,
        final List<ResolveCommitDetails> queue
    ) {
        return Behaviors.setup(ctx -> Behaviors.receive(Command.class)
            .onMessage(
                ResolveCommitDetails.class,
                msg -> waitingForClones(cloner, registrar, materializer, state, queue.append(msg))
            )
            .onMessage(FailedCheckout.class, msg -> {
                msg.msg.replyTo.tell(Done.done());
                if (queue.isEmpty()) {
                    return awaiting(cloner, registrar, materializer, state);
                }
                final var head = queue.head();
                ctx.ask(
                    RepositoryCloner.CloneResponse.class,
                    cloner,
                    Duration.ofMinutes(20),
                    replyTo -> new RepositoryCloner.CloneRepos(
                        head.coordinates.asArtifactCoordinates(), head.gitRepo, replyTo),
                    handleCloneResponse(ctx, head, state)
                );
                queue.tail();
                return waitingForClones(cloner, registrar, materializer, state, queue);
            })
            .onMessage(RepositoriesClonedReadyToResolve.class, msg -> {
                if (msg.checkedOut.isEmpty()) {
                    ctx.getLog().warn(
                        "[{}] No repositories successfully checked out with {}", msg.msg.coordinates, msg.msg.gitRepo);
                    msg.msg.replyTo.tell(Done.done());
                    return completeResolution(cloner, registrar, materializer, state, queue, ctx);
                }
                final ClonedRepoState newState = state.withClonedRepos(msg);
                final var request = msg.msg;
                final var repos = newState.repositoriesCloned.get(
                    request.coordinates.asArtifactCoordinates()).get();
                startCommitResolution(materializer, ctx, request, repos);

                return waitingForResolution(cloner, registrar, materializer, newState, queue);
            })
            .build());
    }

    private static Behavior<Command> waitingForResolution(
        final ActorRef<RepositoryCloner.CloneCommand> cloner,
        final ActorRef<CommitDetailsRegistrar.Command> registrar,
        final Materializer materializer,
        final ClonedRepoState state,
        final List<ResolveCommitDetails> queue
    ) {
        return Behaviors.setup(ctx -> Behaviors.receive(Command.class)
            .onMessage(
                ResolveCommitDetails.class,
                msg -> waitingForResolution(cloner, registrar, materializer, state, queue.append(msg))
            )
            .onMessage(RepositoriesClonedReadyToResolve.class, msg -> {
                final ClonedRepoState newState = state.withClonedRepos(msg);
                return waitingForResolution(cloner, registrar, materializer, newState, queue.append(msg.msg));
            })
            .onMessage(WrappedCommitResolutionResult.class, msg -> {
                final var request = msg.msg;
                final var result = msg.result;
                if (result instanceof AssetCommitResolver.CommitNotFound notFound) {
                    registrar.tell(
                        new CommitDetailsRegistrar.CommitNotFound(
                            notFound.uris().head(),
                            notFound.commit(),
                            request.coordinates,
                            msg.msg.replyTo
                        )
                    );
                } else if (result instanceof AssetCommitResolver.CommitResolved resolved) {
                    if (ctx.getLog().isTraceEnabled()) {
                        ctx.getLog().info("[{}] Resolved commit {}", request.coordinates, resolved.commit().link());
                    }
                    final var commit = resolved.commit();

                    registrar.tell(
                        new CommitDetailsRegistrar.HandleVersionedCommitReport(resolved.repo(), commit,
                            request.coordinates,
                            request.replyTo
                        ));
                }
                return Behaviors.same();
            })
            .onMessage(CompletedResolutionAttempts.class, msg -> {
                ctx.getLog().info("[{}] Completed commit {} resolution", msg.msg.coordinates.asStandardCoordinates(), msg.msg.commit);
                msg.msg.replyTo.tell(Done.done());
                return completeResolution(cloner, registrar, materializer, state, queue, ctx);
            })
            .onMessage(ExceptionallyCompletedResultion.class, msg -> {
                msg.msg.replyTo.tell(Done.done());
                return completeResolution(cloner, registrar, materializer, state, queue, ctx);
            })
            .build()
        );
    }

    private static Behavior<Command> completeResolution(
        ActorRef<RepositoryCloner.CloneCommand> cloner, ActorRef<CommitDetailsRegistrar.Command> registrar,
        Materializer materializer, ClonedRepoState state, List<ResolveCommitDetails> queue, ActorContext<Command> ctx
    ) {
        if (queue.isEmpty()) {
            return awaiting(cloner, registrar, materializer, state);
        }
        final var head = queue.head();
        final var artifactCoordinates = head.coordinates.asArtifactCoordinates();
        if (state.repositoriesCloned.get(artifactCoordinates).isEmpty()) {
            ctx.ask(
                RepositoryCloner.CloneResponse.class,
                cloner,
                Duration.ofMinutes(20),
                replyTo -> new RepositoryCloner.CloneRepos(
                    head.coordinates.asArtifactCoordinates(), head.gitRepo, replyTo),
                handleCloneResponse(ctx, head, state)
            );
            return waitingForClones(cloner, registrar, materializer, state, queue.tail());
        }
        final var artifactRepoStates = state.repositoriesCloned.get(artifactCoordinates).get();

        startCommitResolution(materializer, ctx, head, artifactRepoStates);

        return waitingForResolution(cloner, registrar, materializer, state, queue.tail());
    }


    private static Function2<RepositoryCloner.CloneResponse, Throwable, Command> handleCloneResponse(
        ActorContext<Command> ctx, ResolveCommitDetails msg, ClonedRepoState empty
    ) {
        return (reply, throwable) -> {
            if (throwable != null) {
                ctx.getLog().error("Failed to clone repo", throwable);
                return new FailedCheckout(msg, empty);
            }
            if (reply instanceof RepositoryCloner.SuccessfullyCloned sc) {
                return new RepositoriesClonedReadyToResolve(
                    msg,
                    new ClonedRepoState(HashMap.empty()),
                    sc.checkedOut()
                );
            }
            return new FailedCheckout(msg, empty);
        };
    }

    private static void startCommitResolution(
        Materializer materializer, ActorContext<Command> ctx, ResolveCommitDetails head,
        ArtifactRepoState artifactRepoStates
    ) {
        final var flow = interpretResult(head);
        final Source<Command, NotUsed> via = AssetCommitResolver.startCommitResolution(ctx, artifactRepoStates, head)
            .async()
            .via(flow);
        final Sink<Command, NotUsed> sink = ActorSink.actorRef(
            ctx.getSelf(),
            new CompletedResolutionAttempts(head),
            t -> new ExceptionallyCompletedResultion(head, t)
        );
        via.to(sink).run(materializer);
    }

    private static Flow<AssetCommitResolver.CommitResolutionResponse, Command, NotUsed> interpretResult(
        ResolveCommitDetails msg
    ) {
        return Flow.fromFunction(
            response -> new WrappedCommitResolutionResult(
                msg, response));
    }

    @JsonDeserialize
    public record ArtifactRepoState(
        Set<URI> repositories,
        Map<URI, Path> checkedOut,
        Set<URI> inUse
    ) implements Jsonable {
        @JsonCreator
        public ArtifactRepoState {
        }
    }

    @JsonDeserialize
    public record ClonedRepoState(
        Map<ArtifactCoordinates, ArtifactRepoState> repositoriesCloned
    ) implements Jsonable {
        static final ClonedRepoState EMPTY = new ClonedRepoState(HashMap.empty());

        @JsonCreator
        public ClonedRepoState {
        }

        public ClonedRepoState withClonedRepos(RepositoriesClonedReadyToResolve msg) {
            final var coordinates = msg.msg.coordinates.asArtifactCoordinates();

            if (this.repositoriesCloned.containsKey(coordinates)) {
                return this;
            }
            final var artifactState = new ArtifactRepoState(
                msg.msg.gitRepo.toSet(),
                msg.checkedOut,
                msg.checkedOut.keySet()
            );
            final var newRepositories = this.repositoriesCloned.put(coordinates, artifactState);
            return new ClonedRepoState(newRepositories);
        }

        public ClonedRepoState reset() {
            return new ClonedRepoState(this.repositoriesCloned.mapValues(s -> new ArtifactRepoState(
                s.repositories,
                s.checkedOut,
                HashSet.empty()
            )));
        }
    }

    private record RepositoriesClonedReadyToResolve(
        ResolveCommitDetails msg,
        ClonedRepoState state,
        Map<URI, Path> checkedOut
    ) implements Command {
    }

    private record FailedCheckout(ResolveCommitDetails msg, ClonedRepoState state) implements Command {
    }

    private record WrappedCommitResolutionResult(
        ResolveCommitDetails msg,
        AssetCommitResolver.CommitResolutionResponse result
    ) implements Command {
    }

    private record CompletedResolutionAttempts(
        ResolveCommitDetails msg
    ) implements Command {
    }

    private record ExceptionallyCompletedResultion(
        ResolveCommitDetails msg,
        Throwable error
    ) implements Command {
    }

}
