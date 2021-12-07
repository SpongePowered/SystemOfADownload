package org.spongepowered.synchronizer.versionsync;

import akka.Done;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.TimerScheduler;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.RecoveryCompleted;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;
import akka.persistence.typed.javadsl.SignalHandler;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.auth.api.utils.AuthUtils;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.api.models.VersionRegistration;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
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

    public static Behavior<SyncRegistration> create(
        final AuthUtils auth, final VersionsService service, final PersistenceId persistenceId
    ) {
        return Behaviors.setup(ctx ->
            Behaviors.withTimers(timers -> new ArtifactVersionSyncEntity(ctx, timers, auth, service, persistenceId)));
    }

    public ArtifactVersionSyncEntity(
        final ActorContext<SyncRegistration> ctx,
        final TimerScheduler<SyncRegistration> timers,
        final AuthUtils auth,
        final VersionsService service,
        final PersistenceId persistenceId
    ) {
        super(persistenceId);
        this.ctx = ctx;
        this.timers = timers;
        this.auth = auth;
        this.service = service;
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
                (state, evt) -> state.acceptBatch(evt.artifact(), evt.coordinates())
            )
            .onEvent(VersionSyncEvent.RegisteredVersion.class, (state, evt) -> state.acceptVersion(evt.coordinates()))
            .onEvent(VersionSyncEvent.ResolvedVersion.class, (state, evt) -> state.resolvedVersion(evt.coordinates()))
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
                        () -> this.timers.startSingleTimer(SyncRegistration.Timeout.INSTANCE, Duration.ofSeconds(1)))
                    .thenNoReply();
            })
            .onCommand(SyncRegistration.MarkRegistered.class, (state, cmd) -> this.Effect()
                .persist(new VersionSyncEvent.ResolvedVersion(cmd.coordinates()))
                .thenNoReply()
            )
            .onCommand(SyncRegistration.RetryFailed.class, (state, cmd) -> {
                    final var request = this.createRegistrationRequest(cmd.coordinates());
                    this.ctx.pipeToSelf(request.serviceCall().get(), request.onComplete()::apply);
                    return this.Effect()
                        .noReply();
                }
            )
            .onCommand(SyncRegistration.Refresh.class, (state, cmd) -> {
                if (state.isActive()) {
                    final List<MavenCoordinates> pending = state.getPending();
                    this.ctx.getLog().info("Still awaiting for versions to complete registration: {}", pending);
                    this.timers.startSingleTimer(SyncRegistration.Refresh.INSTANCE, Duration.ofSeconds(2));
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
                .thenRun(() -> this.timers.startSingleTimer(SyncRegistration.Timeout.INSTANCE, Duration.ofSeconds(1)))
                .thenReply(cmd.replyTo(), ns -> Done.done()))
            .onCommand(SyncRegistration.Timeout.class, (state, cmd) -> this.registerVersionsInBatches(state))
        ;
        return builder.build();
    }

    private void checkIfStillHasPending(VersionRegistrationState state) {
        // Go ahead and ask the state to update for the next batch
        if (!state.isActive()) {
            this.timers.startSingleTimer(SyncRegistration.Refresh.INSTANCE, Duration.ofMillis(500));
        }
    }

    private ReplyEffect<VersionSyncEvent, VersionRegistrationState> registerVersionsInBatches(
        VersionRegistrationState state
    ) {
        if (state.isActive()) {
            return this.Effect().noReply();
        }
        final List<MavenCoordinates> batched = state.getNextBatch();
        if (batched.isEmpty()) {
            return this.Effect().noReply();
        }
        final var map = batched.map(this::createRegistrationRequest);
        return this.Effect()
            .persist(new VersionSyncEvent.StartedBatchRegistration(batched))
            .thenRun(ns -> map.forEach(p -> this.ctx.pipeToSelf(p.serviceCall().get(), p.onComplete()::apply)))
            .thenRun(ns -> this.timers.startSingleTimer(SyncRegistration.Refresh.INSTANCE, Duration.ofMillis(100)))
            .thenNoReply();
    }

    private VersionRegistrationParams createRegistrationRequest(MavenCoordinates c) {

        final var serviceCallSupplier = (Supplier<CompletableFuture<VersionRegistration.Response>>) () -> this.auth.internalAuth(
                this.service.registerArtifactCollection(c.groupId, c.artifactId))
            .invoke(new VersionRegistration.Register.Version(c))
            .toCompletableFuture();
        final var onComplete = (BiFunction<VersionRegistration.Response, Throwable, SyncRegistration>) (ok, failure) -> {
            if (failure != null) {
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
            this.timers.startSingleTimer(SyncRegistration.Refresh.INSTANCE, Duration.ofMillis(500));
        });
        return super.signalHandler();
    }
}
