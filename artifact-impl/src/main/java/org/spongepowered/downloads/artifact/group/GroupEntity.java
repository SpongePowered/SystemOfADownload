package org.spongepowered.downloads.artifact.group;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import io.vavr.control.Try;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.utils.UUIDType5;

import java.net.URL;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("unchecked")
public class GroupEntity
    extends PersistentEntity<GroupEntity.GroupCommand, GroupEntity.GroupEvent, GroupEntity.GroupState> {

    public sealed interface GroupEvent extends AggregateEvent<GroupEvent>, Jsonable {

        AggregateEventTag<GroupEvent> INSTANCE = AggregateEventTag.of(GroupEvent.class);

        @Override
        default AggregateEventTagger<GroupEvent> aggregateTag() {
            return INSTANCE;
        }

        final record GroupRegistered(String groupId, String name, String website) implements GroupEvent {
        }
    }

    public sealed interface GroupCommand {

        final record GetGroup(String groupId) implements GroupCommand, PersistentEntity.ReplyType<GroupResponse> {
        }

        final record GetArtifacts(String groupId)
            implements GroupCommand, PersistentEntity.ReplyType<GetArtifactsResponse> {
        }

        final record RegisterArtifact(String artifact)
            implements GroupCommand, PersistentEntity.ReplyType<ArtifactRegistration.Response> {
        }

        final record RegisterGroup(String mavenCoordinates, String name, String website)
            implements GroupCommand, PersistentEntity.ReplyType<GroupRegistration.Response> {
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

    private void respondToGetGroup(final GroupCommand.GetGroup cmd, final ReadOnlyCommandContext<GroupResponse> ctx) {
        if (this.state().groupCoordinates.equals(cmd.groupId)) {

            final String website = this.state().website;
            ctx.reply(Try.of(() -> new URL(website))
                .<GroupResponse>mapTry(url -> new GroupResponse.Available(
                    new Group(this.state().groupCoordinates, this.state().name, website)))
                .getOrElseGet(throwable -> new GroupResponse.Missing(cmd.groupId)));
            return;
        }
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
