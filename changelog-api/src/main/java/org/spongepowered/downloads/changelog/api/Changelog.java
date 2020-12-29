package org.spongepowered.downloads.changelog.api;

import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.git.api.Commit;

import java.util.Objects;

public final class Changelog {
    private final Artifact artifact;
    private final List<Commit> commits;

    public Changelog(Artifact artifact, List<Commit> commits) {
        this.artifact = artifact;
        this.commits = commits;
    }

    public Artifact artifact() {
        return this.artifact;
    }

    public List<Commit> commits() {
        return this.commits;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Changelog) obj;
        return Objects.equals(this.artifact, that.artifact) &&
            Objects.equals(this.commits, that.commits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.artifact, this.commits);
    }

    @Override
    public String toString() {
        return "Changelog[" +
            "artifact=" + this.artifact + ", " +
            "commits=" + this.commits + ']';
    }
}
