/*
 * This file is part of SystemOfADownload, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://spongepowered.org/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
