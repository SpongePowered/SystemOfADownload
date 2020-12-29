package org.spongepowered.downloads.artifact.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;
import java.util.StringJoiner;

public final record Artifact(
    @Schema(required = true)
    String variant,
    Group group,
    @Schema(required = true)
    String artifactId,
    @Schema(required = true)
    String version,
    @Schema(required = true)
    String downloadUrl,
    @Schema(required = true)
    String md5,
    @Schema(required = true)
    String sha1
) {

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
