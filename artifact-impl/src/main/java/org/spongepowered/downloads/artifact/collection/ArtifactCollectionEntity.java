package org.spongepowered.downloads.artifact.collection;

import akka.Done;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.query.GetVersionsResponse;

import java.util.Optional;

@SuppressWarnings("unchecked")
public final class ArtifactCollectionEntity extends PersistentEntity<ArtifactCollectionEntity.Command, ArtifactCollectionEntity.Event, ArtifactCollectionEntity.State> {

    public sealed interface Event extends Jsonable, AggregateEvent<Event> {
        AggregateEventTag<Event> INSTANCE = AggregateEventTag.of(Event.class);

        @Override
        default AggregateEventTagger<Event> aggregateTag() {
            return INSTANCE;
        }

        final record ArtifactGroupUpdated(String groupId) implements Event {}
        final record ArtifactIdUpdated(String artifactId) implements Event {}
        final record ArtifactVersionRegistered(String version, ArtifactCollection collection) implements Event {}
        final record CollectionRegistered(ArtifactCollection collection) implements Event { }
    }

    public sealed interface Command extends Jsonable {

        final record RegisterCollection(ArtifactCollection collection) implements Command, PersistentEntity.ReplyType<NotUsed> {}

        final record GetVersions(String groupId, String artifactId) implements Command, PersistentEntity.ReplyType<GetVersionsResponse> {}
    }

    public static class State {

        private final String groupId;
        private final String artifactId;
        private final Map<String, ArtifactCollection> collection;

        public static State empty() {
            return new State("", "", HashMap.empty());
        }

        public State(final String groupId, final String artifactId, final Map<String, ArtifactCollection> collection) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.collection = collection;
        }
    }


    @Override
    public Behavior initialBehavior(
        final Optional<State> snapshotState
    ) {
        final BehaviorBuilder builder = this.newBehaviorBuilder(snapshotState.orElseGet(State::empty));
        // Registration of artifacts
        builder.setCommandHandler(Command.RegisterCollection.class, this::handleRegisterCommand);
        builder.setEventHandler(Event.ArtifactGroupUpdated.class, this::updateGroupId);
        builder.setEventHandler(Event.ArtifactIdUpdated.class, this::updateArtifactId);
        builder.setEventHandler(Event.ArtifactVersionRegistered.class, this::updateVersionRegistered);

        builder.setReadOnlyCommandHandler(Command.GetVersions.class, this::respondToGetVersions);
        return builder.build();
    }

    private Persist<Event> handleRegisterCommand(final Command.RegisterCollection cmd, final CommandContext<NotUsed> ctx) {
        final String groupCoordinates = cmd.collection().getGroup().getGroupCoordinates();
        if (!this.state().groupId.equals(groupCoordinates)) {
            ctx.thenPersist(new Event.ArtifactGroupUpdated(groupCoordinates));
        }
        final String artifactId = cmd.collection().getArtifactId();
        if (!this.state().artifactId.equals(artifactId)) {
            ctx.thenPersist(new Event.ArtifactIdUpdated(artifactId));
        }
        final String version = cmd.collection().getVersion();
        if (!this.state().collection.containsKey(version)) {
            ctx.thenPersist(new Event.ArtifactVersionRegistered(version, cmd.collection()));
        }
        return ctx.done();
    }

    private State updateGroupId(final Event.ArtifactGroupUpdated event) {
        return new State(event.groupId, this.state().artifactId, this.state().collection);
    }
    private State updateArtifactId(final Event.ArtifactIdUpdated event) {
        return new State(this.state().groupId, event.artifactId(), this.state().collection);
    }
    private State updateVersionRegistered(final Event.ArtifactVersionRegistered event) {
        return new State(
            this.state().groupId,
            this.state().artifactId,
            this.state().collection.put(event.version, event.collection)
        );
    }

    private void respondToGetVersions(final Command.GetVersions cmd, final ReadOnlyCommandContext<GetVersionsResponse> ctx) {
        if (!this.state().groupId.equals(cmd.groupId())) {
            ctx.reply(new GetVersionsResponse.GroupUnknown(cmd.groupId()));
            return;
        }
        if (!this.state().artifactId.equals(cmd.artifactId())) {
            ctx.reply(new GetVersionsResponse.ArtifactUnknown(cmd.artifactId));
            return;
        }
        ctx.reply(new GetVersionsResponse.VersionsAvailable(this.state().collection));


    }

}
