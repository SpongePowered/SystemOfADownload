package org.spongepowered.downloads.artifact.api;

import java.util.Objects;
import java.util.StringJoiner;

public final class Artifact {

    private final Group group;
    private final String artifactId;
    private final String version;

    public Artifact(final Group group, final String artifactId, final String version) {
        this.group = group;
        this.artifactId = artifactId;
        this.version = version;
    }

    public Group getGroup() {
        return this.group;
    }

    public String getArtifactId() {
        return this.artifactId;
    }

    public String getVersion() {
        return this.version;
    }

    public String getFormattedString(final String delimiter) {
        if (Objects.requireNonNull(delimiter, "Delimiter cannot be null!").isEmpty()) {
            throw new IllegalArgumentException("Delimiter cannot be an empty string!");
        }
        final StringJoiner stringJoiner = new StringJoiner(delimiter);
        return stringJoiner
            .add(this.group.getGroupCoordinates())
            .add(this.artifactId)
            .add(this.version)
            .toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final Artifact artifact = (Artifact) o;
        return Objects.equals(this.group, artifact.group) &&
            Objects.equals(this.artifactId, artifact.artifactId) &&
            Objects.equals(this.version, artifact.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.group, this.artifactId, this.version);
    }

    @Override
    public String toString() {
        return new StringJoiner(
            ", ", Artifact.class.getSimpleName() + "[", "]")
            .add("group=" + this.group)
            .add("artifactId='" + this.artifactId + "'")
            .add("version='" + this.version + "'")
            .toString();
    }
}
