package org.spongepowered.downloads.git;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.List;
import org.spongepowered.downloads.git.api.Commit;
import org.spongepowered.downloads.git.api.CommitDiff;
import org.spongepowered.downloads.git.api.Repository;
import org.spongepowered.downloads.git.api.RepositoryRegistration;

import java.io.Serial;
import java.util.Objects;
import java.util.UUID;

public interface CommitCommand {

    final static class CreateCommit implements CommitCommand, Jsonable, PersistentEntity.ReplyType<Commit> {
        @Serial private static final long serialVersionUID = 0L;
        private final Commit commit;

        public CreateCommit(
            Commit commit
        ) {
            this.commit = commit;
        }

        public Commit commit() {
            return this.commit;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (CreateCommit) obj;
            return Objects.equals(this.commit, that.commit);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.commit);
        }

        @Override
        public String toString() {
            return "CreateCommit[" +
                "commit=" + this.commit + ']';
        }

    }

    final static class RegisterRepositoryCommand
        implements CommitCommand, Jsonable, PersistentEntity.ReplyType<Repository> {
        @Serial private static final long serialVersionUID = 0L;
        private final RepositoryRegistration repositoryRegistration;
        private final UUID generatedId;

        public RegisterRepositoryCommand(
            RepositoryRegistration repositoryRegistration,
            UUID generatedId
        ) {
            this.repositoryRegistration = repositoryRegistration;
            this.generatedId = generatedId;
        }

        public RepositoryRegistration repositoryRegistration() {
            return this.repositoryRegistration;
        }

        public UUID generatedId() {
            return this.generatedId;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (RegisterRepositoryCommand) obj;
            return Objects.equals(this.repositoryRegistration, that.repositoryRegistration) &&
                Objects.equals(this.generatedId, that.generatedId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.repositoryRegistration, this.generatedId);
        }

        @Override
        public String toString() {
            return "RegisterRepositoryCommand[" +
                "repositoryRegistration=" + this.repositoryRegistration + ", " +
                "generatedId=" + this.generatedId + ']';
        }

    }

    final static class GetCommitsBetween implements CommitCommand, Jsonable, PersistentEntity.ReplyType<List<Commit>> {
        @Serial private static final long serialVersionUID = 0L;
        private final String repo;
        private final CommitDiff diff;

        public GetCommitsBetween(
            String repo,
            CommitDiff diff
        ) {
            this.repo = repo;
            this.diff = diff;
        }

        public String repo() {
            return this.repo;
        }

        public CommitDiff diff() {
            return this.diff;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (GetCommitsBetween) obj;
            return Objects.equals(this.repo, that.repo) &&
                Objects.equals(this.diff, that.diff);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.repo, this.diff);
        }

        @Override
        public String toString() {
            return "GetCommitsBetween[" +
                "repo=" + this.repo + ", " +
                "diff=" + this.diff + ']';
        }


    }


}
