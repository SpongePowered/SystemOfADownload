package org.spongepowered.downloads.artifact.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;
import java.util.StringJoiner;

public final class Artifact {
    @Schema(required = true) private final String variant;
    private final Group group;
    @Schema(required = true) private final String artifactId;
    @Schema(required = true) private final String version;
    @Schema(required = true) private final String downloadUrl;
    @Schema(required = true) private final String md5;
    @Schema(required = true) private final String sha1;

    public Artifact(
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
        this.variant = variant;
        this.group = group;
        this.artifactId = artifactId;
        this.version = version;
        this.downloadUrl = downloadUrl;
        this.md5 = md5;
        this.sha1 = sha1;
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

    @Schema(required = true)
    public String variant() {
        return this.variant;
    }

    public Group group() {
        return this.group;
    }

    @Schema(required = true)
    public String artifactId() {
        return this.artifactId;
    }

    @Schema(required = true)
    public String version() {
        return this.version;
    }

    @Schema(required = true)
    public String downloadUrl() {
        return this.downloadUrl;
    }

    @Schema(required = true)
    public String md5() {
        return this.md5;
    }

    @Schema(required = true)
    public String sha1() {
        return this.sha1;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Artifact) obj;
        return Objects.equals(this.variant, that.variant) &&
            Objects.equals(this.group, that.group) &&
            Objects.equals(this.artifactId, that.artifactId) &&
            Objects.equals(this.version, that.version) &&
            Objects.equals(this.downloadUrl, that.downloadUrl) &&
            Objects.equals(this.md5, that.md5) &&
            Objects.equals(this.sha1, that.sha1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            this.variant, this.group, this.artifactId, this.version, this.downloadUrl, this.md5, this.sha1);
    }

    @Override
    public String toString() {
        return "Artifact[" +
            "variant=" + this.variant + ", " +
            "group=" + this.group + ", " +
            "artifactId=" + this.artifactId + ", " +
            "version=" + this.version + ", " +
            "downloadUrl=" + this.downloadUrl + ", " +
            "md5=" + this.md5 + ", " +
            "sha1=" + this.sha1 + ']';
    }


}
