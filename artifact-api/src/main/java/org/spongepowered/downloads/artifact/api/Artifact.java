package org.spongepowered.downloads.artifact.api;

import java.util.Objects;
import java.util.StringJoiner;

public final record Artifact(
    String variant,
    Group group,
    String artifactId,
    String version,
    String downloadUrl,
    String md5,
    String sha1) {

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

}
