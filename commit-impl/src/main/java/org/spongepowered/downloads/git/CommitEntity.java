package org.spongepowered.downloads.git;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.SortedMultimap;
import io.vavr.collection.TreeMultimap;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.spongepowered.downloads.git.api.Commit;
import org.spongepowered.downloads.git.api.Repository;
import org.spongepowered.downloads.git.api.RepositoryRegistration;

import java.util.Optional;

public class CommitEntity extends PersistentEntity<CommitCommand, CommitEvent, CommitsState> {
    private static final Logger LOGGER = LogManager.getLogger(CommitEntity.class);

    @SuppressWarnings("unchecked")
    @Override
    public Behavior initialBehavior(final Optional<CommitsState> snapshotState) {
        final BehaviorBuilder builder = this.newBehaviorBuilder(
            snapshotState.orElseGet(CommitsState::empty));
        builder.setCommandHandler(CommitCommand.CreateCommit.class,
            (create, ctx) -> {

                final List<CommitEvent> events = List.empty();
                return ctx.thenPersistAll(events.asJava());
            }
        );
        builder.setCommandHandler(CommitCommand.RegisterRepositoryCommand.class,
            (registerRepo, ctx) -> {
                if (this.state().repositories.containsKey(registerRepo.generatedId)) {
                    ctx.invalidCommand("Repository already registered");
                    return ctx.done();
                }
                final var registration = registerRepo.repositoryRegistration;
                final var repository = new Repository.Builder()
                    .setId(registerRepo.generatedId)
                    .setName(registration.name)
                    .setRepoUrl(registration.gitUrl)
                    .setWebsite(registration.website)
                    .build();

                final var event = new CommitEvent.GitRepoRegistered(repository);
                final var events = java.util.List.of(event);
                return ctx.thenPersistAll(events, () -> ctx.reply(repository));
            }
        );
        builder.setEventHandler(CommitEvent.CommitCreated.class, event -> {
            final CommitsState currentState = this.state();

            currentState.repositoryCommits.get(event.commit.getRepo())
        });
        builder.setEventHandler(CommitEvent.GitRepoRegistered.class, event -> {
            final CommitsState state = this.state();
            final var newCommitsMap = state.repositoryCommits.put(
                event.repository,
                TreeMultimap.withSeq().empty()
            );

            final CommitsState.Builder stateBuilder = new CommitsState.Builder()
                .repositories(newCommitsMap.keySet());

            newCommitsMap.forEach(stateBuilder::repositoryBranchCommits);

            return stateBuilder.build();
        });
        return builder.build();
    }

}
