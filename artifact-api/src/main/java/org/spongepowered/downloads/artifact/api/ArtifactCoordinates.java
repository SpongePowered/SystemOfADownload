package org.spongepowered.downloads.artifact.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Objects;
import java.util.StringJoiner;

@JsonDeserialize
public final class ArtifactCoordinates {

    /**
     * The group id of an artifact, as defined by the Apache Maven documentation.
     * See <a href="https://maven.apache.org/pom.html#Maven_Coordinates">Maven Coordinates</a>.
     */
    @JsonProperty(required = true)
    public final String groupId;
    /**
     * The artifact id of an artifact, as defined by the Apache Maven documentation.
     * See <a href="https://maven.apache.org/pom.html#Maven_Coordinates">Maven Coordinates</a>.
     */
    @JsonProperty(required = true)
    public final String artifactId;


    public ArtifactCoordinates(final String groupId, final String artifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ArtifactCoordinates that = (ArtifactCoordinates) o;
        return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ArtifactCoordinates.class.getSimpleName() + "[", "]")
            .add("groupId='" + groupId + "'")
            .add("artifactId='" + artifactId + "'")
            .toString();
    }

    public MavenCoordinates version(String version) {
        return MavenCoordinates.parse(new StringJoiner(":").add(this.groupId).add(this.artifactId).add(version).toString());
    }
}
