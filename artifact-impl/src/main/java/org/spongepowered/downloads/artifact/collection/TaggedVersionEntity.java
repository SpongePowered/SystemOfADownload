package org.spongepowered.downloads.artifact.collection;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.query.GetTaggedArtifacts;

import java.util.Optional;

@SuppressWarnings("unchecked")
public class TaggedVersionEntity
    extends PersistentEntity<TaggedVersionEntity.Command, TaggedVersionEntity.Event, TaggedVersionEntity.TaggedState> {

    @Override
    public Behavior initialBehavior(
        final Optional<TaggedState> snapshotState
    ) {
        final BehaviorBuilder builder = this.newBehaviorBuilder(snapshotState.orElseGet(TaggedState.EmptyState::new));

        builder.setReadOnlyCommandHandler(Command.RequestTaggedVersions.class, this::handleRequestVersions);
        return builder.build();
    }

    private void handleRequestVersions(
        final Command.RequestTaggedVersions cmd,
        final ReadOnlyCommandContext<GetTaggedArtifacts.Response> ctx
    ) {

    }

    public sealed interface Command {
        final record RequestTaggedVersions(
            int limit,
            int offset
        ) implements Command, PersistentEntity.ReplyType<GetTaggedArtifacts.Response> {}
        final record RegisterTag(
            String tagVersion
        ) implements Command, PersistentEntity.ReplyType<NotUsed> {}
    }

    public sealed interface Event extends Jsonable {

        final record CreatedTaggedVersion(String mavenVersion, String tag, String tagValue) implements Event {}
    }

    public sealed interface TaggedState {

        final record EmptyState() implements TaggedState {}
    }
}
