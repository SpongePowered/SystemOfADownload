package org.spongepowered.downloads.maven.artifact;

import io.vavr.collection.List;

import java.util.Objects;
import java.util.Optional;

public final class Versioning {
    public final String latest;
    public final String release;
    public final String lastUpdated;
    public final List<String> versions;

    public Versioning() {
        this.latest = "";
        this.release = "";
        this.lastUpdated = "";
        this.versions = List.empty();
    }

    Versioning(
        final Optional<String> latest, final Optional<String> release,
        final String lastUpdated,
        final List<String> versions
    ) {
        this.latest = latest.orElse("");
        this.release = release.orElse("");
        this.lastUpdated = lastUpdated;
        this.versions = versions;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        final var that = (Versioning) obj;
        return Objects.equals(this.latest, that.latest) &&
            Objects.equals(this.release, that.release) &&
            Objects.equals(this.lastUpdated, that.lastUpdated) &&
            Objects.equals(this.versions, that.versions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.latest, this.release, this.lastUpdated, this.versions);
    }

    @Override
    public String toString() {
        return "Versioning[" +
            "latest=" + this.latest + ", " +
            "release=" + this.release + ", " +
            "lastUpdated=" + this.lastUpdated + ", " +
            "versions=" + this.versions+ ']';
    }
}
