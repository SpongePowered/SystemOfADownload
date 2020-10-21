package org.spongepowered.downloads.git.api;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;
import java.util.StringJoiner;

@Immutable
public class CommitDiff {

    public final CommitSha fromSha;
    public final CommitSha toSha;

    public CommitDiff(final CommitSha fromSha, final CommitSha toSha) {
        this.fromSha = fromSha;
        this.toSha = toSha;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final CommitDiff that = (CommitDiff) o;
        return Objects.equals(this.fromSha, that.fromSha) &&
            Objects.equals(this.toSha, that.toSha);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.fromSha, this.toSha);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", CommitDiff.class.getSimpleName() + "[", "]")
            .add("fromSha='" + this.fromSha + "'")
            .add("toSha='" + this.toSha + "'")
            .toString();
    }
}
