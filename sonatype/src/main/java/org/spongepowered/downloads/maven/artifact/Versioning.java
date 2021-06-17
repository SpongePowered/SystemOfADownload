package org.spongepowered.downloads.maven.artifact;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.vavr.collection.List;

import java.util.Objects;
import java.util.Optional;

@JsonDeserialize
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

    @JsonCreator
    public Versioning(
        @JsonProperty("latest") final String latest,
        @JsonProperty("release") final String release,
        @JsonProperty("lastUpdated") String lastUpdated,
        @JsonProperty("versions") List<String> versions
    ) {
        this.latest = latest == null ? "" : latest;
        this.release = release == null ? "" : release;
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
