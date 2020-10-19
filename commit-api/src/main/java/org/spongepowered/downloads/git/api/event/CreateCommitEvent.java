package org.spongepowered.downloads.git.api.event;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.StringJoiner;

public final class CreateCommitEvent {

    public final String gitRepoUrl;
    public final long commitSha1;
    public final long commitSha2;
    public final int commitSha3;
    public final String author;
    public final ZonedDateTime commitDate;
    public final String header;
    public final String message;

    private CreateCommitEvent(final Builder builder) {
        this.gitRepoUrl = builder.gitRepoUrl;
        this.commitSha1 = builder.commitSha1;
        this.commitSha2 = builder.commitSha2;
        this.commitSha3 = builder.commitSha3;
        this.author = builder.author;
        this.commitDate = builder.commitDate;
        this.header = builder.header;
        this.message = builder.message;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final CreateCommitEvent that = (CreateCommitEvent) o;
        return this.commitSha1 == that.commitSha1 &&
            this.commitSha2 == that.commitSha2 &&
            this.commitSha3 == that.commitSha3 &&
            this.gitRepoUrl.equals(that.gitRepoUrl) &&
            this.author.equals(that.author) &&
            this.commitDate.equals(that.commitDate) &&
            this.header.equals(that.header) &&
            this.message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.gitRepoUrl, this.commitSha1, this.commitSha2, this.commitSha3, this.author, this.commitDate, this.header, this.message);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", CreateCommitEvent.class.getSimpleName() + "[", "]")
            .add("gitRepoUrl='" + this.gitRepoUrl + "'")
            .add("commitSha1=" + this.commitSha1)
            .add("commitSha2=" + this.commitSha2)
            .add("commitSha3=" + this.commitSha3)
            .add("author='" + this.author + "'")
            .add("commitDate=" + this.commitDate)
            .add("header='" + this.header + "'")
            .add("message='" + this.message + "'")
            .toString();
    }

    public static final class Builder {
        private @Nullable String gitRepoUrl;
        private long commitSha1;
        private long commitSha2;
        private int commitSha3;
        private @Nullable String author;
        private @Nullable ZonedDateTime commitDate;
        private @Nullable String header;
        private @Nullable String message;

        public Builder() { }

        public void setGitRepoUrl(final String gitRepoUrl) {
            this.gitRepoUrl = gitRepoUrl;
        }

        public void setCommitSha1(final long commitSha1) {
            this.commitSha1 = commitSha1;
        }

        public void setCommitSha2(final long commitSha2) {
            this.commitSha2 = commitSha2;
        }

        public void setCommitSha3(final int commitSha3) {
            this.commitSha3 = commitSha3;
        }

        public void setAuthor(final String author) {
            this.author = author;
        }

        public void setCommitDate(final ZonedDateTime commitDate) {
            this.commitDate = commitDate;
        }

        public void setHeader(final String header) {
            this.header = header;
        }

        public void setMessage(final String message) {
            this.message = message;
        }

        /**
         * Builds a new commit event given this builder.
         *
         * @return The commit event
         */
        @RequiresNonNull({"gitRepoUrl", "author", "commitDate", "header", "message"})
        public CreateCommitEvent build() {
            if (this.gitRepoUrl == null) {
                throw new IllegalStateException("Git repo cannot be null!");
            }
            if (this.commitSha1 == 0 || this.commitSha2 == 0 || this.commitSha3 == 0) {
                throw new IllegalStateException("Commit sha not completed!");
            }
            if (this.author == null) {
                throw new IllegalStateException("Git author cannot be null!");
            }
            if (this.commitDate == null) {
                throw new IllegalStateException("Git commitDate cannot be null!");
            }
            if (this.header == null) {
                throw new IllegalStateException("message header cannot be null!");
            }
            if (this.message == null) {
                throw new IllegalStateException("Commit message cannot be null!");
            }
            return new CreateCommitEvent(this);
        }
    }
}
