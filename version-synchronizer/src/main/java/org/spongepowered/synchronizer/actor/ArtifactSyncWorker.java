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
package org.spongepowered.synchronizer.actor;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Routers;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.stream.SinkShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.RunnableGraph;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorSink;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.auth.api.utils.AuthUtils;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.api.models.VersionRegistration;
import org.spongepowered.synchronizer.resync.ArtifactSynchronizerAggregate;
import org.spongepowered.synchronizer.resync.Resync;

public final class ArtifactSyncWorker {

    public interface Command {
    }

    /**
     * Starts the request that the specific artifact described by the
     * {@link ArtifactCoordinates coordinates} are synchronized, versioned,
     * and other side effects. A {@link Done} response is always given,
     * regardless of outcome, since the sync performs multiple jobs in the
     * background.
     */
    public static final record PerformResync(ArtifactCoordinates coordinates, ActorRef<Done> replyTo)
        implements Command {
    }

    /**
     * For use with just getting a {@link Done} reply back, some messages can be ignored.
     */
    public static final record Ignored(ActorRef<Done> replyTo) implements Command {
    }

    private static final record Failed(ArtifactCoordinates coordinates, ActorRef<Done> replyTo) implements Command {
    }

    /**
     * A post-reqeusted result signifying that the initial request for an
     * artifact's versions to sync has been completed.
     */
    private static final record WrappedResult(ActorRef<Done> replyTo) implements Command {
    }

    static final Flow<MavenCoordinates, RequestSingleRegistration, NotUsed> coordinatesFlow = Flow.fromFunction(
        RequestSingleRegistration::new);

    @SuppressWarnings("unchecked")
    public static Behavior<Command> create(
        final VersionsService versionService,
        final ClusterSharding clusterSharding
    ) {
        return Behaviors.setup(ctx -> {
            final ArtifactSyncExtension.Settings settings = ArtifactSyncExtension.SettingsProvider.get(ctx.getSystem());
            final var dispatch = DispatcherSelector.defaultDispatcher();
            final AuthUtils auth = AuthUtils.configure(ctx.getSystem().settings().config());
            final var pool = Routers.pool(
                settings.poolSize,
                Behaviors.supervise(registerNewVersion(versionService, auth)).onFailure(SupervisorStrategy.resume())
            );
            ctx.getLog().debug("Maven Sync Settings: \n{}\n", settings);
            final var registrationRef = ctx.spawn(
                pool,
                "version-registration-attempt",
                dispatch
            );

            final var registrationSink = ActorSink.actorRef(registrationRef, new Finished(), r -> new Finished());

            final Sink<MavenCoordinates, NotUsed> coordinatesRegistration = Sink.fromGraph(GraphDSL.create(b -> {
                final var add = b.add(coordinatesFlow);
                final var outlet = b.add(registrationSink);
                b.from(add.out())
                    .toInlet(outlet.in());
                return new SinkShape<>(add.in());
            }));

            return awaiting(clusterSharding, coordinatesRegistration, settings);
        });
    }

    private static Behavior<Command> awaiting(
        final ClusterSharding clusterSharding,
        final Sink<MavenCoordinates, NotUsed> fanOutVersions,
        final ArtifactSyncExtension.Settings settings
    ) {
        return Behaviors.setup(ctx -> Behaviors.receive(Command.class)
            .onMessage(PerformResync.class, msg -> {
                ctx.getLog().debug("Running Sync");
                ctx.pipeToSelf(
                    clusterSharding.entityRefFor(
                            ArtifactSynchronizerAggregate.ENTITY_TYPE_KEY,
                            msg.coordinates.groupId + ":" + msg.coordinates.artifactId
                        )
                        .<List<MavenCoordinates>>ask(
                            replyTo -> new Resync(msg.coordinates, replyTo), settings.individualTimeOut)
                        .thenApply(response ->
                            RunnableGraph
                                .fromGraph(
                                    Source.from(response).to(fanOutVersions)
                                )
                                .run(ctx.getSystem().classicSystem())
                        ),
                    (ok, exception) -> {
                        if (exception != null) {
                            ctx.getLog().error("Failed to resync by maven coordinates, may ask again", exception);
                            return new Failed(msg.coordinates, msg.replyTo);
                        }

                        return new WrappedResult(msg.replyTo);
                    }
                );
                return Behaviors.same();
            })
            .onMessage(WrappedResult.class, msg -> {
                msg.replyTo.tell(Done.done());
                return Behaviors.same();
            })
            .onMessage(Ignored.class, msg -> {
                msg.replyTo.tell(Done.done());
                return Behaviors.same();
            })
            .onMessage(Failed.class, msg -> {
                msg.replyTo.tell(Done.done());
                return Behaviors.same();
            })
            .build());
    }

    private interface Child {
    }

    private static final record RequestSingleRegistration(MavenCoordinates coordinates)
        implements Child {
    }

    private static final record FailedRegistration(MavenCoordinates coordinates)
        implements Child {
    }

    private static final record Finished() implements Child {

    }

    private static final record Completed(MavenCoordinates coordinates) implements Child {
    }

    private static final record Redundant(MavenCoordinates coordinates) implements Child {
    }

    private static Behavior<Child> registerNewVersion(
        final VersionsService versionsService,
        final AuthUtils auth
    ) {
        return Behaviors.setup(ctx -> Behaviors.receive(Child.class)
            .onMessage(RequestSingleRegistration.class, msg -> {
                performRegistration(versionsService, auth, ctx, msg.coordinates);
                return queuedRegistrations(versionsService, auth, msg.coordinates, List.empty());
            })
            .onMessage(Finished.class, msg -> {
                return Behaviors.same();
            })
            .build());

    }

    private static Behavior<Child> queuedRegistrations(
        final VersionsService service,
        final AuthUtils auth,
        final MavenCoordinates working,
        final List<MavenCoordinates> pending
    ) {
        return Behaviors.setup(ctx -> {
            ctx.getLog().debug("Starting work on {} with {} in queue", working, pending.size());
            return Behaviors.receive(Child.class)
                .onMessage(
                    RequestSingleRegistration.class,
                    msg -> queuedRegistrations(service, auth, working, pending.append(msg.coordinates))
                )
                .onMessage(Completed.class, msg -> {
                    ctx.getLog().debug("Completed work on {}. {} remain in queue", working, pending.size());
                    if (pending.isEmpty()) {
                        return registerNewVersion(service, auth);
                    }
                    final var next = pending.head();
                    performRegistration(service, auth, ctx, next);
                    return queuedRegistrations(service, auth, next, pending.tail());
                })
                .onMessage(Redundant.class, msg -> {
                    ctx.getLog().debug("Redundant work on {}, {} in queue", working, pending.size());
                    if (pending.isEmpty()) {
                        return registerNewVersion(service, auth);
                    }
                    final var next = pending.head();
                    performRegistration(service, auth, ctx, next);
                    return queuedRegistrations(service, auth, next, pending.tail());
                })
                .onMessage(FailedRegistration.class, msg -> {
                    ctx.getLog().warn(
                        "Failed version registration of {}, re-attempting with {} in queue", msg.coordinates,
                        pending.size()
                    );
                    performRegistration(service, auth, ctx, working);
                    return Behaviors.same();
                })
                .onMessage(Finished.class, msg -> {
                    ctx.getLog().debug(
                        "Received end of stream, working on {} with {} in queue", working, pending.size());
                    return Behaviors.same();
                })
                .build();
        });
    }

    private static void performRegistration(
        VersionsService service, AuthUtils auth, ActorContext<Child> ctx, MavenCoordinates next
    ) {
        ctx.pipeToSelf(
            auth.internalAuth(service.registerArtifactCollection(next.groupId, next.artifactId))
                .invoke(new VersionRegistration.Register.Version(next)),
            (ok, failure) -> {
                if (failure != null) {
                    ctx.getLog().error(
                        String.format(
                            "Received error trying to synchronize %s",
                            next.asStandardCoordinates()
                        ), failure);
                    return new FailedRegistration(next);
                }
                if (ok instanceof VersionRegistration.Response.ArtifactAlreadyRegistered a) {
                    ctx.getLog().trace("Redundant registration of {}", a.coordinates());
                    return new Redundant(next);
                } else if (ok instanceof VersionRegistration.Response.GroupMissing gm) {
                    ctx.getLog().error("Group missing for {}", gm.groupId());
                    return new FailedRegistration(next);
                } else if (ok instanceof VersionRegistration.Response.RegisteredArtifact r) {
                    ctx.getLog().trace("Successful registration of {}", r.mavenCoordinates());
                    return new Completed(next);
                }
                ctx.getLog().warn("Failed registration synchronizing {} with response {}", next, ok);
                return new FailedRegistration(next);
            }
        );
    }
}
