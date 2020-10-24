package org.spongepowered.downloads.git;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.List;
import org.spongepowered.downloads.git.api.Commit;
import org.spongepowered.downloads.git.api.CommitDiff;
import org.spongepowered.downloads.git.api.Repository;
import org.spongepowered.downloads.git.api.RepositoryRegistration;

import java.util.UUID;

public sealed interface CommitCommand
    permits CommitCommand.CreateCommit,
        CommitCommand.RegisterRepositoryCommand,
        CommitCommand.GetCommitsBetween {

    final record CreateCommit(
        Commit commit
    ) implements CommitCommand, Jsonable, PersistentEntity.ReplyType<Commit> {
    }

    final record RegisterRepositoryCommand(
        RepositoryRegistration repositoryRegistration,
        UUID generatedId
    ) implements CommitCommand, Jsonable, PersistentEntity.ReplyType<Repository> {
    }

    final record GetCommitsBetween(
        String repo,
        CommitDiff diff
    ) implements CommitCommand, Jsonable, PersistentEntity.ReplyType<List<Commit>> {

    }


}
