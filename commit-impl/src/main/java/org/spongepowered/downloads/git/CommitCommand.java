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
            final Commit commit
        ) {
            this.commit = commit;
        }

        public Commit commit() {
            return this.commit;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (CreateCommit) obj;
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
            final RepositoryRegistration repositoryRegistration,
            final UUID generatedId
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
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (RegisterRepositoryCommand) obj;
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
            final String repo,
            final CommitDiff diff
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
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (GetCommitsBetween) obj;
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
