/*
 * This file is part of SystemOfADownload, licensed under the MIT License (MIT).
 *
 * Copyright (c) "SpongePowered" <"https://www.spongepowered.org/">
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

import io.vavr.collection.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

public final class Commit {

    public final long shardId1;
    public final long shardId2;
    public final int shardId3;
    public final UUID repo;
    public final String header;
    public final String messageBody;
    public final String author;
    public final ZonedDateTime commitDate;
    public final List<SubmoduleCommit> subCommits;

    private Commit(final Builder builder) {
        this.shardId1 = builder.shardId1;
        this.shardId2 = builder.shardId2;
        this.shardId3 = builder.shardId3;
        this.repo = builder.repo;
        this.header = builder.header;
        this.messageBody = builder.messageBody;
        this.author = builder.author;
        this.commitDate = builder.commitDate;
        this.subCommits = builder.subCommits;
    }

    public static final class Builder {
        private @MonotonicNonNull long shardId1;
        private @MonotonicNonNull long shardId2;
        private @MonotonicNonNull int shardId3;
        private @MonotonicNonNull UUID repo;
        private @MonotonicNonNull String header;
        private @MonotonicNonNull String messageBody;
        private @MonotonicNonNull String author;
        private @MonotonicNonNull ZonedDateTime commitDate;
        private @MonotonicNonNull List<SubmoduleCommit> subCommits;

        public Builder() { }

        public void setShardId1(final long shardId1) {
            this.shardId1 = shardId1;
        }

        public void setShardId2(final long shardId2) {
            this.shardId2 = shardId2;
        }

        public void setShardId3(final int shardId3) {
            this.shardId3 = shardId3;
        }

        public void setRepo(final UUID repo) {
            this.repo = repo;
        }

        public void setHeader(final String header) {
            this.header = header;
        }

        public void setMessageBody(final String messageBody) {
            this.messageBody = messageBody;
        }

        public void setAuthor(final String author) {
            this.author = author;
        }

        public void setCommitDate(final ZonedDateTime commitDate) {
            this.commitDate = Objects.requireNonNull(commitDate, "Commit date");
        }

        public void setSubCommits(final List<SubmoduleCommit> subCommits) {
            this.subCommits = subCommits;
        }

        public Commit build() {
            return new Commit(this);
        }
    }
}
