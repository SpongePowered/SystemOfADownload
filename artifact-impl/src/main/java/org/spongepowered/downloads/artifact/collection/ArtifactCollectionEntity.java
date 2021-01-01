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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.query.GetVersionsResponse;

import java.io.Serial;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings("unchecked")
public final class ArtifactCollectionEntity extends PersistentEntity<ArtifactCollectionEntity.Command, ArtifactCollectionEntity.Event, ArtifactCollectionEntity.State> {

    public static final Logger LOGGER = LogManager.getLogger("ArtifactEntity");
    public static final Marker DATA_RETRIEVAL = MarkerManager.getMarker("READ");

    public interface Event extends Jsonable, AggregateEvent<Event> {
        AggregateEventTag<Event> INSTANCE = AggregateEventTag.of(Event.class);

        @Override
        default AggregateEventTagger<Event> aggregateTag() {
            return INSTANCE;
        }

        final static class ArtifactGroupUpdated implements Event {
            @Serial private static final long serialVersionUID = 0L;
            private final String groupId;

            public ArtifactGroupUpdated(final String groupId) {
                this.groupId = groupId;
            }

            public String groupId() {
                return this.groupId;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (ArtifactGroupUpdated) obj;
                return Objects.equals(this.groupId, that.groupId);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.groupId);
            }

            @Override
            public String toString() {
                return "ArtifactGroupUpdated[" +
                    "groupId=" + this.groupId + ']';
            }
        }

        final static class ArtifactIdUpdated implements Event {
            @Serial private static final long serialVersionUID = 0L;
            private final String artifactId;

            public ArtifactIdUpdated(final String artifactId) {
                this.artifactId = artifactId;
            }

            public String artifactId() {
                return this.artifactId;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (ArtifactIdUpdated) obj;
                return Objects.equals(this.artifactId, that.artifactId);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.artifactId);
            }

            @Override
            public String toString() {
                return "ArtifactIdUpdated[" +
                    "artifactId=" + this.artifactId + ']';
            }
        }

        final static class ArtifactVersionRegistered implements Event {
            @Serial private static final long serialVersionUID = 0L;
            private final String version;
            private final ArtifactCollection collection;

            public ArtifactVersionRegistered(final String version, final ArtifactCollection collection) {
                this.version = version;
                this.collection = collection;
            }

            public String version() {
                return this.version;
            }

            public ArtifactCollection collection() {
                return this.collection;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (ArtifactVersionRegistered) obj;
                return Objects.equals(this.version, that.version) &&
                    Objects.equals(this.collection, that.collection);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.version, this.collection);
            }

            @Override
            public String toString() {
                return "ArtifactVersionRegistered[" +
                    "version=" + this.version + ", " +
                    "collection=" + this.collection + ']';
            }
        }

        final static class CollectionRegistered implements Event {
            @Serial private static final long serialVersionUID = 0L;
            private final ArtifactCollection collection;

            public CollectionRegistered(final ArtifactCollection collection) {
                this.collection = collection;
            }

            public ArtifactCollection collection() {
                return this.collection;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (CollectionRegistered) obj;
                return Objects.equals(this.collection, that.collection);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.collection);
            }

            @Override
            public String toString() {
                return "CollectionRegistered[" +
                    "collection=" + this.collection + ']';
            }
        }
    }

    public interface Command extends Jsonable {

        final static class RegisterCollection implements Command, ReplyType<NotUsed> {
            @Serial private static final long serialVersionUID = 0L;
            private final ArtifactCollection collection;

            public RegisterCollection(final ArtifactCollection collection) {
                this.collection = collection;
            }

            public ArtifactCollection collection() {
                return this.collection;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (RegisterCollection) obj;
                return Objects.equals(this.collection, that.collection);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.collection);
            }

            @Override
            public String toString() {
                return "RegisterCollection[" +
                    "collection=" + this.collection + ']';
            }
        }

        final static class GetVersions implements Command, ReplyType<GetVersionsResponse> {
            @Serial private static final long serialVersionUID = 0L;
            private final String groupId;
            private final String artifactId;

            public GetVersions(final String groupId, final String artifactId) {
                this.groupId = groupId;
                this.artifactId = artifactId;
            }

            public String groupId() {
                return this.groupId;
            }

            public String artifactId() {
                return this.artifactId;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (GetVersions) obj;
                return Objects.equals(this.groupId, that.groupId) &&
                    Objects.equals(this.artifactId, that.artifactId);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.groupId, this.artifactId);
            }

            @Override
            public String toString() {
                return "GetVersions[" +
                    "groupId=" + this.groupId + ", " +
                    "artifactId=" + this.artifactId + ']';
            }
        }
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
