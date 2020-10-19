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