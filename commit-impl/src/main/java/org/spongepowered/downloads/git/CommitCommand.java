package org.spongepowered.downloads.git;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.git.api.Commit;

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

}
