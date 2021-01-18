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
package org.spongepowered.downloads.git.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.vavr.collection.Multimap;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;
import java.util.StringJoiner;

@JsonDeserialize
@Immutable
public class Commit {

    private final CommitSha sha;
    private final Author author;
    private final Repository repo;
    private final Multimap<String, Commit> submoduleCommits;

    public Commit(
        final CommitSha sha, final Author author, final Repository repo,
        final Multimap<String, Commit> submoduleCommits
    ) {
        this.sha = sha;
        this.author = author;
        this.repo = repo;
        this.submoduleCommits = submoduleCommits;
    }

    public Author getAuthor() {
        return this.author;
    }

    public Repository getRepo() {
        return this.repo;
    }

    public Multimap<String, Commit> getSubmoduleCommits() {
        return this.submoduleCommits;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final Commit commit = (Commit) o;
        return this.author.equals(commit.author) &&
            this.repo.equals(commit.repo) &&
            this.submoduleCommits.equals(commit.submoduleCommits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.author, this.repo, this.submoduleCommits);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Commit.class.getSimpleName() + "[", "]")
            .add("author=" + this.author)
            .add("repo=" + this.repo)
            .add("submoduleCommits=" + this.submoduleCommits)
            .toString();
    }
}