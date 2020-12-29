package org.spongepowered.downloads.git.api;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;

@Immutable
public final class CommitDiff {
    private final CommitSha fromSha;
    private final CommitSha toSha;

    public CommitDiff(
        CommitSha fromSha,
        CommitSha toSha
    ) {
        this.fromSha = fromSha;
        this.toSha = toSha;
    }

    public CommitSha fromSha() {
        return this.fromSha;
    }

    public CommitSha toSha() {
        return this.toSha;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (CommitDiff) obj;
        return Objects.equals(this.fromSha, that.fromSha) &&
            Objects.equals(this.toSha, that.toSha);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.fromSha, this.toSha);
    }

    @Override
    public String toString() {
        return "CommitDiff[" +
            "fromSha=" + this.fromSha + ", " +
            "toSha=" + this.toSha + ']';
    }

}
