package org.spongepowered.downloads.artifact.sonatype;

import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;

import java.time.Instant;
import java.util.Objects;

public final class ArtifactSynchronizerAggregate extends EventSourcedBehaviorWithEnforcedReplies<ArtifactSynchronizerAggregate.Resync, ArtifactSynchronizerAggregate.SynchronizeEvent, ArtifactSynchronizerAggregate.SyncState> {
    public static EntityTypeKey<Resync> ENTITY_TYPE_KEY = EntityTypeKey.create(Resync.class, "ArtifactSynchronizer");

    public ArtifactSynchronizerAggregate(EntityContext<Resync> context) {
        super(
            // PersistenceId needs a typeHint (or namespace) and entityId,
            // we take then from the EntityContext
            PersistenceId.of(
                context.getEntityTypeKey().name(), // <- type hint
                context.getEntityId() // <- business id
            ));
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
            .onEvent(SynchronizedArtifacts.class, (event) -> new SyncState(event.groupId, event.artifactId, event.updatedTime));
        return builder.build();
    }

    @Override
    public CommandHandlerWithReply<Resync, SynchronizeEvent, SyncState> commandHandler() {
        final var builder = this.newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(Resync.class, (state, cmd) -> {
                final var effect = this.Effect();
                return effect.none();
            });
        return null;
    }

    public static enum Resync { INSTANCE }

    static interface SynchronizeEvent extends Jsonable, AggregateEvent<SynchronizeEvent> {

        AggregateEventShards<SynchronizeEvent> TAG = AggregateEventTag.sharded(SynchronizeEvent.class, 10);

        @Override
        default AggregateEventTagger<SynchronizeEvent> aggregateTag() {
            return TAG;
        }
    }

    @JsonDeserialize
    static final class SynchronizedArtifacts implements SynchronizeEvent {

        public final String groupId;
        public final String artifactId;
        public final Instant updatedTime;

        @JsonCreator
        public SynchronizedArtifacts(final String groupId, final String artifactId, final Instant updatedTime) {
            this.groupId = Objects.requireNonNull(groupId, "groupId");
            this.artifactId = Objects.requireNonNull(artifactId, "artifactId");
            this.updatedTime = Objects.requireNonNull(updatedTime, "updatedTime");
        }
    }

    static final class SyncState {

        public final String groupId;
        public final String artifactId;
        public final Instant lastUpdated;

        static final SyncState EMPTY = new SyncState("", "", Instant.EPOCH);

        public SyncState(final String groupId, final String artifactId, final Instant lastUpdated) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.lastUpdated = lastUpdated;
        }
    }

}
