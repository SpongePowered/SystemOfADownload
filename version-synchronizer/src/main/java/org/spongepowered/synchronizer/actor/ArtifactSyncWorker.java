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
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Routers;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.event.Logging;
import akka.stream.Attributes;
import akka.stream.FlowShape;
import akka.stream.UniformFanInShape;
import akka.stream.UniformFanOutShape;
import akka.stream.javadsl.Balance;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Merge;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorFlow;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.auth.utils.AuthUtils;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.api.models.VersionRegistration;
import org.spongepowered.synchronizer.resync.ArtifactSynchronizerAggregate;
import org.spongepowered.synchronizer.resync.Resync;

import java.util.stream.IntStream;

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
                Behaviors.supervise(registerNewVersion(versionService, auth)).onFailure(SupervisorStrategy.restart())
            );
            final var registrationRef = ctx.spawn(
                pool,
                "version-registration-attempt",
                dispatch
            );
            final var flow = ActorFlow.ask(
                settings.versionFanoutParallelism, registrationRef, settings.timeOut,
                RequestSingleRegistration::new
            );

            final Flow<MavenCoordinates, NotUsed, NotUsed> fanOutVersions = Flow.fromGraph(
                GraphDSL.create(b -> {
                    final UniformFanOutShape<MavenCoordinates, MavenCoordinates> balance = b.add(
                        Balance.create(settings.parallelism));
                    final UniformFanInShape<NotUsed, NotUsed> merge = b.add(Merge.create(settings.parallelism));
                    IntStream.range(0, settings.parallelism)
                        .forEach(i -> b.from(balance.out(i))
                            .via(b.add(flow.async()))
                            .toInlet(merge.in(i)));
                    return FlowShape.of(balance.in(), merge.out());
                }));
            return awaiting(clusterSharding, fanOutVersions, settings);
        });
    }

    private static Behavior<Command> awaiting(
        final ClusterSharding clusterSharding,
        final Flow<MavenCoordinates, NotUsed, NotUsed> fanOutVersions,
        final ArtifactSyncExtension.Settings settings
    ) {
        return Behaviors.setup(ctx -> Behaviors.receive(Command.class)
            .onMessage(PerformResync.class, msg -> {
                ctx.pipeToSelf(
                    clusterSharding.entityRefFor(
                            ArtifactSynchronizerAggregate.ENTITY_TYPE_KEY,
                            msg.coordinates.groupId + ":" + msg.coordinates.artifactId
                        )
                        .<List<MavenCoordinates>>ask(
                            replyTo -> new Resync(msg.coordinates, replyTo), settings.individualTimeOut),
                    (ok, exception) -> {
                        if (exception != null) {
                            ctx.getLog().error("Failed to resync by maven coordinates, may ask again", exception);
                            return new Failed(msg.coordinates, msg.replyTo);
                        }
                        Source.from(ok)
                            .withAttributes(Attributes.createLogLevels(
                                Logging.WarningLevel(),
                                Logging.WarningLevel(),
                                Logging.ErrorLevel()
                            ))
                            .via(fanOutVersions.async())
                            .to(Sink.ignore())
                            .run(ctx.getSystem());
                        return new WrappedResult(msg.replyTo);
                    }
                );
                return awaiting(clusterSharding, fanOutVersions, settings);
            })
            .onMessage(WrappedResult.class, msg -> {
                msg.replyTo.tell(Done.done());
                return awaiting(clusterSharding, fanOutVersions, settings);
            })
            .onMessage(Ignored.class, msg -> {
                msg.replyTo.tell(Done.done());
                return awaiting(clusterSharding, fanOutVersions, settings);
            })
            .onMessage(Failed.class, msg -> {
                msg.replyTo.tell(Done.done());
                return awaiting(clusterSharding, fanOutVersions, settings);
            })
            .build());
    }

    private interface Child {
    }

    private static final record RequestSingleRegistration(MavenCoordinates coordinates, ActorRef<NotUsed> replyTo)
        implements Child {
    }

    private static final record FailedRegistration(MavenCoordinates coordinates, ActorRef<NotUsed> replyTo)
        implements Child {
    }

    private static final record Completed(MavenCoordinates coordinates, ActorRef<NotUsed> replyTo) implements Child {
    }

    private static final record Redundant(MavenCoordinates coordinates, ActorRef<NotUsed> replyTo) implements Child {
    }

    private static Behavior<Child> registerNewVersion(
        final VersionsService versionsService,
        final AuthUtils auth
    ) {
        return Behaviors.setup(ctx -> Behaviors.receive(Child.class)
            .onMessage(RequestSingleRegistration.class, msg -> {
                ctx.pipeToSelf(
                    versionsService.registerArtifactCollection(msg.coordinates.groupId, msg.coordinates.artifactId)
                        .handleRequestHeader(
                            requestHeader -> requestHeader.withHeader(
                                auth.internalHeaderKey(),
                                auth.internalHeaderSecret()
                            ))
                        .invoke(new VersionRegistration.Register.Version(msg.coordinates)),
                    (ok, failure) -> {
                        if (failure != null) {
                            ctx.getLog().error(
                                String.format(
                                    "Received error trying to synchronize %s",
                                    msg.coordinates().asStandardCoordinates()
                                ), failure);
                            return new FailedRegistration(msg.coordinates, msg.replyTo);
                        }
                        if (ok instanceof VersionRegistration.Response.ArtifactAlreadyRegistered) {
                            return new Redundant(msg.coordinates, msg.replyTo);
                        } else if (ok instanceof VersionRegistration.Response.GroupMissing) {
                            return new FailedRegistration(msg.coordinates, msg.replyTo);
                        } else if (ok instanceof VersionRegistration.Response.RegisteredArtifact) {
                            return new Completed(msg.coordinates, msg.replyTo);
                        }
                        return new FailedRegistration(msg.coordinates, msg.replyTo);
                    }
                );
                return Behaviors.same();
            })
            .onMessage(FailedRegistration.class, msg -> {
                ctx.getLog().error("Somehow got to impossible state with registration handling");
                msg.replyTo.tell(NotUsed.notUsed());
                return Behaviors.same();
            })
            .onMessage(Completed.class, msg -> {
                msg.replyTo.tell(NotUsed.notUsed());
                return Behaviors.same();
            })
            .onMessage(Redundant.class, msg -> {
                msg.replyTo.tell(NotUsed.notUsed());
                return Behaviors.same();
            })
            .build());

    }
}
