package org.spongepowered.downloads.git;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.git.api.Commit;
import org.spongepowered.downloads.git.api.Repository;

import java.io.Serial;
import java.util.Objects;

public interface CommitEvent extends Jsonable, AggregateEvent<CommitEvent> {

    AggregateEventTag<CommitEvent> INSTANCE = AggregateEventTag.of(CommitEvent.class);

    @Override
    default AggregateEventTagger<CommitEvent> aggregateTag() {
        return INSTANCE;
    }

    @JsonDeserialize
    final static class CommitCreated implements CommitEvent {
        @Serial private static final long serialVersionUID = 0L;
        private final Commit commit;

        public CommitCreated(Commit commit) {
            this.commit = commit;
        }

        public Commit commit() {
            return this.commit;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (CommitCreated) obj;
            return Objects.equals(this.commit, that.commit);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.commit);
        }

        @Override
        public String toString() {
            return "CommitCreated[" +
                "commit=" + this.commit + ']';
        }

    }

    @JsonDeserialize
    final static class GitRepoRegistered implements CommitEvent {
        @Serial private static final long serialVersionUID = 0L;
        private final Repository repository;

        public GitRepoRegistered(Repository repository) {
            this.repository = repository;
        }

        public Repository repository() {
            return this.repository;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (GitRepoRegistered) obj;
            return Objects.equals(this.repository, that.repository);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.repository);
        }

        @Override
        public String toString() {
            return "GitRepoRegistered[" +
                "repository=" + this.repository + ']';
        }

    }

}
