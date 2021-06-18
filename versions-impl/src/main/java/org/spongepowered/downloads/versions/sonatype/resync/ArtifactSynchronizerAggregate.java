package org.spongepowered.downloads.versions.sonatype.resync;

import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.japi.function.Function;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.sonatype.client.SonatypeClient;

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
        final var builder = this.newCommandHandlerWithReplyBuilder()
            .forAnyState()
            .onCommand(Resync.class, this::handleResync);
        return builder.build();
    }

    private ReplyEffect<SynchronizeEvent, SyncState> handleResync(SyncState state, Resync cmd) {
        final var groupId = !state.groupId.equals(cmd.coordinates.groupId) ? cmd.coordinates.groupId : state.groupId;
        final var artifactId = !state.artifactId.equals(cmd.coordinates.artifactId) ? cmd.coordinates.artifactId : state.artifactId;
        return this.client.getArtifactMetadata(groupId.replace(".", "/"), artifactId)
            .mapTry(metadata -> {
                if (metadata.versioning().lastUpdated.equals(state.lastUpdated)) {
                    return this.Effect()
                        .reply(cmd.replyTo, List.empty());
                }
                return this.Effect()
                    .persist(new SynchronizeEvent.SynchronizedArtifacts(metadata, metadata.versioning().lastUpdated))
                    .thenReply(cmd.replyTo, stateToCoordinates);
            })
            .getOrElseGet((ignored) -> this.Effect().reply(cmd.replyTo, List.empty()));
    }

}
