package org.spongepowered.downloads.git;

import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.collection.SortedMultimap;
import org.spongepowered.downloads.git.api.Commit;
import org.spongepowered.downloads.git.api.Repository;

import java.util.UUID;
import java.util.function.Function;

public class CommitsState implements Jsonable {

    private static final class Holder {
        private static final CommitsState EMPTY = new Builder().build();
    }

    public static CommitsState empty() {
        return Holder.EMPTY;
    }

    public final Map<Repository, SortedMultimap<String, Commit>> repositoryCommits;
    public Map<UUID, Repository> repositories;
    public Map<String, Repository> repositoryByName;

    private CommitsState(final Builder builder) {
        this.repositoryCommits = builder.repositoryCommits;
        this.repositories = builder.repositories;
        this.repositoryByName = builder.repositoryByName;
    }


    public static final class Builder {
        private Map<Repository, SortedMultimap<String, Commit>> repositoryCommits = HashMap.empty();
        private Map<UUID, Repository> repositories = HashMap.empty();
        private Map<String, Repository> repositoryByName = HashMap.empty();

        public Builder() {
        }

        public Builder repositories(final Set<Repository> repositories) {
            this.repositories = repositories.toSortedMap(UUID::compareTo, Repository::getId, Function.identity());
            this.repositoryByName = repositories.toSortedMap(String::compareTo, Repository::getName, Function.identity());
            return this;
        }

        public Builder repositoryBranchCommits(final Repository repository, final SortedMultimap<String, Commit> commits) {
            if (!this.repositories.containsKey(repository.getId())) {
                throw new IllegalArgumentException("Repository is not registered!");
            }
            this.repositoryCommits = this.repositoryCommits.put(repository, commits);
            return this;
        }

        public CommitsState build() {
            return new CommitsState(this);
        }
    }
}
