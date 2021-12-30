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
package org.spongepowered.synchronizer.versionsync;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Routers;
import akka.actor.typed.javadsl.TimerScheduler;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.pattern.CircuitBreakerOpenException;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.RecoveryCompleted;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;
import akka.persistence.typed.javadsl.RetentionCriteria;
import akka.persistence.typed.javadsl.SignalHandler;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.auth.api.utils.AuthUtils;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.api.models.VersionRegistration;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class ArtifactVersionSyncEntity
    extends EventSourcedBehaviorWithEnforcedReplies<SyncRegistration, VersionSyncEvent, VersionRegistrationState> {

    public static final EntityTypeKey<SyncRegistration> ENTITY_TYPE_KEY = EntityTypeKey.create(
        SyncRegistration.class, "artifact-version-sync");

    private final ActorContext<SyncRegistration> ctx;
    private final TimerScheduler<SyncRegistration> timers;
    private final AuthUtils auth;
    private final VersionsService service;
    private final ActorRef<BatchVersionSyncManager.Command> batchSync;

    public static Behavior<SyncRegistration> create(
        final AuthUtils auth, final VersionsService service, final PersistenceId persistenceId
    ) {

        return Behaviors.setup(ctx -> {
            final var router = Routers.group(BatchVersionSyncManager.KEY);
            final var ref = ctx.spawnAnonymous(router);
            return Behaviors.withTimers(
                timers -> new ArtifactVersionSyncEntity(ctx, timers, auth, service, persistenceId, ref));
        });
    }

    public ArtifactVersionSyncEntity(
        final ActorContext<SyncRegistration> ctx,
        final TimerScheduler<SyncRegistration> timers,
        final AuthUtils auth,
        final VersionsService service,
        final PersistenceId persistenceId,
        final ActorRef<BatchVersionSyncManager.Command> ref
    ) {
        super(persistenceId);
        this.ctx = ctx;
        this.timers = timers;
        this.auth = auth;
        this.service = service;
        this.batchSync = ref;
    }

    @Override
    public VersionRegistrationState emptyState() {
        return new VersionRegistrationState.Empty();
    }

    @Override
    public EventHandler<VersionRegistrationState, VersionSyncEvent> eventHandler() {
        final var builder = this.newEventHandlerBuilder();
        builder.forAnyState()
            .onEvent(
                VersionSyncEvent.RegisteredBatch.class,
                (state, evt) -> {
                    this.batchSync.tell(new BatchVersionSyncManager.ArtifactToSync(evt.artifact()));
                    return state.acceptBatch(evt.artifact(), evt.coordinates());
                }
            )
            .onEvent(VersionSyncEvent.RegisteredVersion.class, (state, evt) -> {
                this.batchSync.tell(
                    new BatchVersionSyncManager.ArtifactToSync(evt.coordinates().asArtifactCoordinates()));
                return state.acceptVersion(evt.coordinates());
            })
            .onEvent(VersionSyncEvent.ResolvedVersion.class, (state, evt) -> {
                this.batchSync.tell(
                    new BatchVersionSyncManager.ArtifactToSync(evt.coordinates().asArtifactCoordinates()));
                return state.resolvedVersion(evt.coordinates());
            })
            .onEvent(VersionSyncEvent.StartedBatchRegistration.class, (state, evt) -> state.startBatch(evt.batched()))
            .onEvent(VersionSyncEvent.FailedVersion.class, (state, evt) -> state.failedVersion(evt.coordinates()))
        ;
        return builder.build();
    }

    @Override
    public CommandHandlerWithReply<SyncRegistration, VersionSyncEvent, VersionRegistrationState> commandHandler() {
        final var builder = this.newCommandHandlerWithReplyBuilder();
        builder.forAnyState()
            .onCommand(SyncRegistration.Register.class, (state, cmd) -> {
                if (state.hasVersion(cmd.coordinates())) {
                    return this.Effect()
                        .noReply();
                }
                return this.Effect()
                    .persist(new VersionSyncEvent.RegisteredVersion(cmd.coordinates()))
                    .thenRun(
                        () -> this.timers.startSingleTimer(
                            cmd.coordinates(), SyncRegistration.Timeout.INSTANCE, Duration.ofSeconds(1)))
                    .thenNoReply();
            })
            .onCommand(SyncRegistration.MarkRegistered.class, (state, cmd) -> this.Effect()
                .persist(new VersionSyncEvent.ResolvedVersion(cmd.coordinates()))
                .thenRun(this::checkIfStillHasPending)
                .thenNoReply()
            )
            .onCommand(SyncRegistration.DelayRegistration.class, (state, cmd) -> {
                this.timers.startSingleTimer(
                    cmd.coordinates(), new SyncRegistration.RetryFailed(cmd.coordinates()), cmd.duration());
                return this.Effect().noReply();
            })
            .onCommand(SyncRegistration.RetryFailed.class, (state, cmd) -> {
                final var request = this.createRegistrationRequest(cmd.coordinates());
                this.ctx.pipeToSelf(request.serviceCall().get(), request.onComplete()::apply);
                return this.Effect()
                    .noReply();
            })
            .onCommand(SyncRegistration.Refresh.class, (state, cmd) -> {
                final List<MavenCoordinates> pending = state.getPending();
                if (state.isActive() && !pending.isEmpty()) {
                    if (this.ctx.getLog().isDebugEnabled()) {
                        this.ctx.getLog().debug("Still awaiting for versions to complete registration: {}", pending);
                    }
                    pending.forEachWithIndex((coordinates, index) -> this.timers.startSingleTimer(coordinates,
                        new SyncRegistration.RetryFailed(coordinates), Duration.ofSeconds((int) (1 + (0.2 * index)))
                    ));
                    this.timers.startSingleTimer("refresh", SyncRegistration.Refresh.INSTANCE, Duration.ofSeconds(20 + pending.size()));
                    return this.Effect().noReply();
                }
                return this.registerVersionsInBatches(state);
            })
            .onCommand(SyncRegistration.AlreadyRegistered.class, (state, cmd) -> this.Effect()
                .persist(new VersionSyncEvent.ResolvedVersion(cmd.coordinates()))
                .thenRun(this::checkIfStillHasPending)
                .thenNoReply()
            )
            .onCommand(SyncRegistration.GroupUnregistered.class, (state, cmd) -> this.Effect()
                .persist(new VersionSyncEvent.FailedVersion(cmd.coordinates()))
                .thenRun(this::checkIfStillHasPending)
                .thenNoReply()
            )
            .onCommand(SyncRegistration.SyncBatch.class, (state, cmd) -> this.Effect()
                .persist(new VersionSyncEvent.RegisteredBatch(cmd.artifact(), cmd.coordinates()))
                .thenRun(() -> this.timers.startSingleTimer("timeout", SyncRegistration.Timeout.INSTANCE,
                    Duration.ofSeconds(1)
                ))
                .thenReply(cmd.replyTo(), ns -> Done.done()))
            .onCommand(SyncRegistration.Timeout.class, (state, cmd) -> this.registerVersionsInBatches(state))
        ;
        return builder.build();
    }

    private void checkIfStillHasPending(VersionRegistrationState state) {
        // Go ahead and ask the state to update for the next batch
        if (!state.isActive()) {
            if (this.ctx.getLog().isTraceEnabled()) {
                this.ctx.getLog().trace("No more pending versions, scheduling a refresh in 500 milliseconds");
            }
            this.timers.startSingleTimer("refresh", SyncRegistration.Refresh.INSTANCE, Duration.ofMillis(500));
        } else {
            if (this.ctx.getLog().isTraceEnabled()) {
                this.ctx.getLog().trace("State is currently active, {}", state.getPending());
            }
        }
    }

    private ReplyEffect<VersionSyncEvent, VersionRegistrationState> registerVersionsInBatches(
        VersionRegistrationState state
    ) {
        if (state.isActive() && !state.getPending().isEmpty()) {
            final var pending = state.getPending();
            this.ctx.getLog().warn("Resubmitting version registration due to pending versions: {}", pending);
            pending.map(this::createRegistrationRequest).forEach(
                p -> this.ctx.pipeToSelf(p.serviceCall().get(), p.onComplete::apply));
            return this.Effect().noReply();
        }
        if (!state.getPending().isEmpty()) {
            if (this.ctx.getLog().isDebugEnabled()) {
                this.ctx.getLog().debug("Rescheduling Refresh due to Pending registrations: {}", state.getPending());
            }
            this.timers.startSingleTimer("refresh", SyncRegistration.Refresh.INSTANCE, Duration.ofSeconds(2));
            this.batchSync.tell(new BatchVersionSyncManager.ArtifactToSync(state.coordinates()));
            return this.Effect().noReply();
        }
        final List<MavenCoordinates> batched = state.getNextBatch();
        if (batched.isEmpty()) {
            if (this.ctx.getLog().isTraceEnabled()) {
                this.ctx.getLog().trace("No more pending batches");
            }
            return this.Effect().noReply();
        }
        final var map = batched.map(this::createRegistrationRequest);
        return this.Effect()
            .persist(new VersionSyncEvent.StartedBatchRegistration(batched))
            .thenRun(ns -> map.forEach(p -> this.ctx.pipeToSelf(p.serviceCall().get(), p.onComplete()::apply)))
            .thenRun(
                ns -> this.timers.startSingleTimer("refresh", SyncRegistration.Refresh.INSTANCE, Duration.ofSeconds(2)))
            .thenRun(ns -> this.batchSync.tell(new BatchVersionSyncManager.ArtifactToSync(ns.coordinates())))
            .thenNoReply();
    }

    private VersionRegistrationParams createRegistrationRequest(MavenCoordinates c) {

        final var serviceCallSupplier = (Supplier<CompletableFuture<VersionRegistration.Response>>) () -> this.auth.internalAuth(
                this.service.registerArtifactCollection(c.groupId, c.artifactId))
            .invoke(new VersionRegistration.Register.Version(c))
            .toCompletableFuture();
        final var onComplete = (BiFunction<VersionRegistration.Response, Throwable, SyncRegistration>) (ok, failure) -> {
            if (failure != null) {
                if (failure instanceof CompletionException ce) {
                    failure = ce.getCause();
                }
                if (failure instanceof CircuitBreakerOpenException cbe) {
                    this.ctx.getLog().warn("Circuit breaker is open, delaying registration of {}", c);
                    return new SyncRegistration.DelayRegistration(
                        c, Duration.ofMillis(cbe.remainingDuration().toMillis()).plus(Duration.ofSeconds(4)));
                } else if (failure instanceof TimeoutException) {
                    ctx.getLog().warn("Rescheduling asset registration for {}", c);
                    return new SyncRegistration.DelayRegistration(c, Duration.ofSeconds(2));
                }
                ctx.getLog().error(
                    String.format(
                        "Received error trying to synchronize %s",
                        c.asStandardCoordinates()
                    ), failure);
                return new SyncRegistration.RetryFailed(c);
            }
            if (ok instanceof VersionRegistration.Response.ArtifactAlreadyRegistered a) {
                ctx.getLog().trace("Redundant registration of {}", a.coordinates());
                return new SyncRegistration.AlreadyRegistered(c);
            } else if (ok instanceof VersionRegistration.Response.GroupMissing gm) {
                ctx.getLog().error("Group missing for {}", gm.groupId());
                return new SyncRegistration.GroupUnregistered(c);
            } else if (ok instanceof VersionRegistration.Response.RegisteredArtifact r) {
                ctx.getLog().trace("Successful registration of {}", r.mavenCoordinates());
                return new SyncRegistration.MarkRegistered(c);
            }
            ctx.getLog().warn("Failed registration synchronizing {} with response {}", c, ok);
            return new SyncRegistration.RetryFailed(c);
        };
        return new VersionRegistrationParams(serviceCallSupplier, onComplete);
    }

    record VersionRegistrationParams(
        Supplier<CompletableFuture<VersionRegistration.Response>> serviceCall,
        BiFunction<VersionRegistration.Response, Throwable, SyncRegistration> onComplete
    ) {
    }

    @Override
    public SignalHandler<VersionRegistrationState> signalHandler() {
        final var builder = this.newSignalHandlerBuilder();
        // Enable restarting our timers
        builder.onSignal(RecoveryCompleted.class, (state, signal) -> {
            this.timers.startSingleTimer("refresh", SyncRegistration.Refresh.INSTANCE, Duration.ofMillis(500));
        });
        return super.signalHandler();
    }

    @Override
    public RetentionCriteria retentionCriteria() {
        return RetentionCriteria.snapshotEvery(100, 2);
    }
}
