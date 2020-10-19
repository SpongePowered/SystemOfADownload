package org.spongepowered.downloads.git;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.Optional;

public class CommitEntity extends PersistentEntity<CommitCommand, CommitEvent, CommitsState> {
    private static final Logger LOGGER = LogManager.getLogger(CommitEntity.class);

    @SuppressWarnings("unchecked")
    @Override
    public Behavior initialBehavior(final Optional<CommitsState> snapshotState) {
        final BehaviorBuilder builder = this.newBehaviorBuilder(
            snapshotState.orElseGet(() -> new CommitsState(HashMap.empty())));
        builder.setCommandHandler(CommitCommand.CreateCommit.class,
            (create, ctx) -> {

                final List<CommitEvent> events = List.empty();
                return ctx.thenPersistAll(events.asJava());
            }
        );
        builder.setEventHandler(CommitEvent.CommitCreated.class, event -> {
            final CommitsState currentState = this.state();

            currentState.repositoryCommits.get(event.commit.getRepo())
        });
        return builder.build();
    }

}
