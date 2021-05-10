package org.spongepowered.downloads.versions.sonatype.resync;

import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.japi.function.Function;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.Effect;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.sonatype.client.SonatypeClient;

import java.time.LocalDateTime;

public final class ArtifactSynchronizerAggregate
    extends EventSourcedBehaviorWithEnforcedReplies<Resync, SynchronizeEvent, SyncState> {
    public static EntityTypeKey<Resync> ENTITY_TYPE_KEY = EntityTypeKey.create(Resync.class, "ArtifactSynchronizer");
    static final Function<SyncState, List<MavenCoordinates>> stateToCoordinates = (s) -> s.versions.versioning().versions.map(
        version -> MavenCoordinates.parse(s.groupId + ":" + s.artifactId + ":" + version));
    private final SonatypeClient client;

    public ArtifactSynchronizerAggregate(EntityContext<Resync> context) {
        super(
            // PersistenceId needs a typeHint (or namespace) and entityId,
            // we take then from the EntityContext
            PersistenceId.of(
                context.getEntityTypeKey().name(), // <- type hint
                context.getEntityId() // <- business id
            ));
        final var mapper = new ObjectMapper();
        this.client = SonatypeClient.configureClient(mapper).get();
    }

    public static ArtifactSynchronizerAggregate create(EntityContext<Resync> context) {
        return new ArtifactSynchronizerAggregate(context);
    }

    @Override
    public SyncState emptyState() {
        return SyncState.EMPTY;
    }

    @Override
    public EventHandler<SyncState, SynchronizeEvent> eventHandler() {
        final var builder = newEventHandlerBuilder()
            .forAnyState()
            .onEvent(
                SynchronizeEvent.SynchronizedArtifacts.class,
                (event) -> new SyncState(event.updatedTime, event.metadata)
            );
        return builder.build();
    }

    @Override
    public CommandHandlerWithReply<Resync, SynchronizeEvent, SyncState> commandHandler() {
        final var builder = this.newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(Resync.class, this::handleResync);
        return null;
    }

    private Effect<SynchronizeEvent, SyncState> handleResync(SyncState state, Resync cmd) {
        return this.client.getArtifactMetadata(state.groupId, state.artifactId)
            .mapTry(metadata -> {
                if (metadata.versioning().lastUpdated.equals(state.lastUpdated)) {
                    return this.Effect()
                        .reply(cmd.replyTo, List.empty());
                }
                return this.Effect()
                    .persist(new SynchronizeEvent.SynchronizedArtifacts(metadata, metadata.versioning().lastUpdated))
                    .thenReply(cmd.replyTo, stateToCoordinates);
            })
            .get();
    }

}
