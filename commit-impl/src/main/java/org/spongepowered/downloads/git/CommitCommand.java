package org.spongepowered.downloads.git;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.List;
import org.spongepowered.downloads.git.api.Commit;
import org.spongepowered.downloads.git.api.CommitDiff;
import org.spongepowered.downloads.git.api.Repository;
import org.spongepowered.downloads.git.api.RepositoryRegistration;

import java.util.UUID;

public interface CommitCommand {

    final class CreateCommit implements
        CommitCommand, Jsonable,
        PersistentEntity.ReplyType<Commit> {

        public final Commit commit;

        @JsonCreator
        public CreateCommit(final Commit commit) {
            this.commit = commit;
        }
    }

    public final class RegisterRepositoryCommand
    implements CommitCommand, Jsonable,
        PersistentEntity.ReplyType<Repository> {

        public final RepositoryRegistration repositoryRegistration;
        public final UUID generatedId;

        @JsonCreator
        public RegisterRepositoryCommand(final RepositoryRegistration repositoryRegistration, final UUID uuid) {
            this.repositoryRegistration = repositoryRegistration;
            this.generatedId = uuid;
        }
    }

    final class GetCommitsBetween implements CommitCommand, Jsonable, PersistentEntity.ReplyType<List<Commit>> {
        public final String repo;
        public final CommitDiff diff;

        public GetCommitsBetween(final String repo, final CommitDiff diff) {
            this.repo = repo;
            this.diff = diff;
        }


    }



}
