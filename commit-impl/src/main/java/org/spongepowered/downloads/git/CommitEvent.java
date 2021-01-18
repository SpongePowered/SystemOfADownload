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

        public CommitCreated(final Commit commit) {
            this.commit = commit;
        }

        public Commit commit() {
            return this.commit;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (CommitCreated) obj;
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

        public GitRepoRegistered(final Repository repository) {
            this.repository = repository;
        }

        public Repository repository() {
            return this.repository;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (GitRepoRegistered) obj;
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
