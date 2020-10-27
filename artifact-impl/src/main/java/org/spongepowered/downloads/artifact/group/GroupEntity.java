package org.spongepowered.downloads.artifact.group;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.control.Try;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.utils.UUIDType5;

import java.net.URL;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("unchecked")
public class GroupEntity extends PersistentEntity<GroupEntity.GroupCommand, GroupEntity.GroupEvent, GroupEntity.GroupState> {

    public sealed interface GroupEvent extends AggregateEvent<GroupEvent>, Jsonable {

        AggregateEventTag<GroupEvent> INSTANCE = AggregateEventTag.of(GroupEvent.class);

        @Override
        default AggregateEventTagger<GroupEvent> aggregateTag() {
            return INSTANCE;
        }

        final record GroupRegistered(String groupId, String name, String website) implements GroupEvent {}

    }

    public static interface GroupCommand {
        public record GetGroup(String groupId) implements GroupCommand, PersistentEntity.ReplyType<GroupResponse> {
        }
    }

    public static final class GroupState {
        private final String groupCoordinates;
        private final String name;
        private final String website;
        private final UUID groupId;

        static GroupState empty() {
            return new GroupState("", "", "https://example.com");
        }

        GroupState(final String groupCoordinates, final String name, final String website) {
            this.groupCoordinates = groupCoordinates;
            this.name = name;
            this.website = website;
            this.groupId = UUIDType5.nameUUIDFromNamespaceAndString(UUIDType5.NAMESPACE_OID, this.groupCoordinates);
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
    }

    @Override
    public Behavior initialBehavior(
        final Optional<GroupState> snapshotState
    ) {
        final BehaviorBuilder builder = this.newBehaviorBuilder(snapshotState.orElseGet(GroupState::empty));

        builder.setEventHandler(GroupEvent.GroupRegistered.class, this::handleRegistration);
        builder.setReadOnlyCommandHandler(GroupCommand.GetGroup.class, (cmd, ctx) -> {
            if (this.state().groupCoordinates.equals(cmd.groupId)) {

                final String website = this.state().website;
                ctx.reply(Try.of(() -> new URL(website))
                    .<GroupResponse>mapTry(url -> new GroupResponse.Available(new Group(this.state().groupCoordinates, this.state().name, url)))
                    .getOrElseGet(throwable -> new GroupResponse.Missing(cmd.groupId)));
                return;
            }
            ctx.reply(new GroupResponse.Missing(cmd.groupId));
        });

        return builder.build();
    }

    private GroupState handleRegistration(final GroupEvent.GroupRegistered event) {
        return new GroupState(event.groupId, event.name, event.website);
    }
}
