package org.spongepowered.downloads.artifact.group;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.serialization.CompressedJsonable;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import io.vavr.control.Try;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.utils.UUIDType5;

import java.io.Serial;
import java.net.URL;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("unchecked")
public class GroupEntity
    extends PersistentEntity<GroupEntity.GroupCommand, GroupEntity.GroupEvent, GroupEntity.GroupState> {

    public static final Logger LOGGER = LogManager.getLogger("GroupEntity");
    public static final Marker STATE_RETRIEVAL = MarkerManager.getMarker("READ");

    public interface GroupEvent extends AggregateEvent<GroupEvent>, Jsonable {

        AggregateEventTag<GroupEvent> INSTANCE = AggregateEventTag.of(GroupEvent.class);

        @Override
        default AggregateEventTagger<GroupEvent> aggregateTag() {
            return INSTANCE;
        }

        final static class GroupRegistered implements GroupEvent {
            @Serial private static final long serialVersionUID = 0L;
            private final String groupId;
            private final String name;
            private final String website;

            public GroupRegistered(final String groupId, final String name, final String website) {
                this.groupId = groupId;
                this.name = name;
                this.website = website;
            }

            public String groupId() {
                return this.groupId;
            }

            public String name() {
                return this.name;
            }

            public String website() {
                return this.website;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (GroupRegistered) obj;
                return Objects.equals(this.groupId, that.groupId) &&
                    Objects.equals(this.name, that.name) &&
                    Objects.equals(this.website, that.website);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.groupId, this.name, this.website);
            }

            @Override
            public String toString() {
                return "GroupRegistered[" +
                    "groupId=" + this.groupId + ", " +
                    "name=" + this.name + ", " +
                    "website=" + this.website + ']';
            }

        }
    }

    public interface GroupCommand extends Jsonable {

        final static class GetGroup implements GroupCommand, ReplyType<GroupResponse> {
            private final String groupId;

            public GetGroup(final String groupId) {
                this.groupId = groupId;
            }

            public String groupId() {
                return this.groupId;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (GetGroup) obj;
                return Objects.equals(this.groupId, that.groupId);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.groupId);
            }

            @Override
            public String toString() {
                return "GetGroup[" +
                    "groupId=" + this.groupId + ']';
            }

        }

        final static class GetArtifacts
            implements GroupCommand, ReplyType<GetArtifactsResponse> {
            private final String groupId;

            public GetArtifacts(final String groupId) {
                this.groupId = groupId;
            }

            public String groupId() {
                return this.groupId;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (GetArtifacts) obj;
                return Objects.equals(this.groupId, that.groupId);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.groupId);
            }

            @Override
            public String toString() {
                return "GetArtifacts[" +
                    "groupId=" + this.groupId + ']';
            }

        }

        final static class RegisterArtifact
            implements GroupCommand, ReplyType<ArtifactRegistration.Response> {
            private final String artifact;

            public RegisterArtifact(final String artifact) {
                this.artifact = artifact;
            }

            public String artifact() {
                return this.artifact;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (RegisterArtifact) obj;
                return Objects.equals(this.artifact, that.artifact);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.artifact);
            }

            @Override
            public String toString() {
                return "RegisterArtifact[" +
                    "artifact=" + this.artifact + ']';
            }

        }

        final static class RegisterGroup
            implements GroupCommand, ReplyType<GroupRegistration.Response> {
            private final String mavenCoordinates;
            private final String name;
            private final String website;

            public RegisterGroup(final String mavenCoordinates, final String name, final String website) {
                this.mavenCoordinates = mavenCoordinates;
                this.name = name;
                this.website = website;
            }

            public String mavenCoordinates() {
                return this.mavenCoordinates;
            }

            public String name() {
                return this.name;
            }

            public String website() {
                return this.website;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (RegisterGroup) obj;
                return Objects.equals(this.mavenCoordinates, that.mavenCoordinates) &&
                    Objects.equals(this.name, that.name) &&
                    Objects.equals(this.website, that.website);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.mavenCoordinates, this.name, this.website);
            }

            @Override
            public String toString() {
                return "RegisterGroup[" +
                    "mavenCoordinates=" + this.mavenCoordinates + ", " +
                    "name=" + this.name + ", " +
                    "website=" + this.website + ']';
            }

        }
    }

    public static final class GroupState {
        private final String groupCoordinates;
        private final String name;
        private final String website;
        private final Set<String> artifacts;
        private final UUID groupId;

        static GroupState empty() {
            return new GroupState("", "", "https://example.com", HashSet.empty());
        }

        GroupState(
            final String groupCoordinates, final String name, final String website, final Set<String> artifacts
        ) {
            this.groupCoordinates = groupCoordinates;
            this.name = name;
            this.website = website;
            this.groupId = UUIDType5.nameUUIDFromNamespaceAndString(UUIDType5.NAMESPACE_OID, this.groupCoordinates);
            this.artifacts = artifacts;
        }

        public String getGroupCoordinates() {
            return this.groupCoordinates;
        }

        public String getName() {
            return this.name;
        }

        public String getWebsite() {
            return this.website;
        }

        public UUID getGroupId() {
            return this.groupId;
        }

        public Set<String> getArtifacts() {
            return this.artifacts;
        }
    }

    @Override
    public Behavior initialBehavior(
        final Optional<GroupState> snapshotState
    ) {
        final BehaviorBuilder builder = this.newBehaviorBuilder(snapshotState.orElseGet(GroupState::empty));

        builder.setEventHandler(GroupEvent.GroupRegistered.class, this::handleRegistration);
        builder.setCommandHandler(GroupCommand.RegisterGroup.class, this::respondToRegisterGroup);
        builder.setCommandHandler(GroupCommand.RegisterArtifact.class, this::respondToRegisterArtifact);

        builder.setReadOnlyCommandHandler(GroupCommand.GetGroup.class, this::respondToGetGroup);
        builder.setReadOnlyCommandHandler(GroupCommand.GetArtifacts.class, this::respondToGetVersions);

        return builder.build();
    }

    private GroupState handleRegistration(final GroupEvent.GroupRegistered event) {
        return new GroupState(event.groupId, event.name, event.website, HashSet.empty());
    }

    private Persist<GroupEvent> respondToRegisterGroup(
        final GroupCommand.RegisterGroup cmd, final CommandContext<GroupRegistration.Response> ctx
    ) {
        if (!this.state().groupCoordinates.equals(cmd.mavenCoordinates)) {
            return ctx.thenPersist(
                new GroupEvent.GroupRegistered(cmd.mavenCoordinates, cmd.name, cmd.website),
                event -> ctx.reply(new GroupRegistration.Response.GroupRegistered(
                    new Group(cmd.mavenCoordinates, cmd.name, cmd.website)))
            );
        }
        ctx.reply(new GroupRegistration.Response.GroupAlreadyRegistered(cmd.mavenCoordinates));
        return ctx.done();
    }

    private Persist<GroupEvent> respondToRegisterArtifact(
        final GroupCommand.RegisterArtifact cmd, final CommandContext<ArtifactRegistration.Response> ctx
    ) {
        if (this.state().artifacts.contains(cmd.artifact())) {
            ctx.reply(new ArtifactRegistration.Response.ArtifactAlreadyRegistered(cmd.artifact, this.state().groupCoordinates));
        }
        return ctx.done();
    }

    private void respondToGetGroup(final GroupCommand.GetGroup cmd, final ReadOnlyCommandContext<GroupResponse> ctx) {
        LOGGER.info(STATE_RETRIEVAL, "CurrentState: {} responding to cmd {}", this.state().getName(), cmd);
        if (this.state().getName().equalsIgnoreCase(cmd.groupId)) {

            LOGGER.info(STATE_RETRIEVAL, "Group Matched, getting response from Stfate: {}", this.state());
            final String website = this.state().website;
            ctx.reply(Try.of(() -> new URL(website))
                .<GroupResponse>mapTry(url -> {
                    LOGGER.info(STATE_RETRIEVAL, "URL matched");
                    final Group group = new Group(this.state().groupCoordinates, this.state().name, website);
                    LOGGER.info(STATE_RETRIEVAL, "Retrieved Group: (coords: {}, name: {}, website: {})", group.groupCoordinates, group.name, group.website);
                    return new GroupResponse.Available(group);
                })
                .getOrElseGet(throwable -> new GroupResponse.Missing(cmd.groupId)));
            return;
        }
        LOGGER.info(STATE_RETRIEVAL, "GroupId mismatch, requested {} but had {}", this.state().name, cmd.groupId);
        ctx.reply(new GroupResponse.Missing(cmd.groupId));
    }

    private void respondToGetVersions(
        final GroupCommand.GetArtifacts cmd, final ReadOnlyCommandContext<GetArtifactsResponse> ctx
    ) {
        if (this.state().groupCoordinates.isEmpty()) {
            ctx.reply(new GetArtifactsResponse.GroupMissing(cmd.groupId()));
            return;
        }
        ctx.reply(new GetArtifactsResponse.ArtifactsAvailable(this.state().artifacts.toList()));
    }
}
