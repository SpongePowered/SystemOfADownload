/*
 * This file is part of SystemOfADownload, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://spongepowered.org/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.downloads.artifact.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;
import java.util.StringJoiner;

@JsonSerialize
public final class Artifact {
    @JsonProperty @Schema(required = true) private final String variant;
    @JsonIgnore private final Group group;
    @JsonProperty @Schema(required = true) private final String artifactId;
    @JsonProperty @Schema(required = true) private final String version;
    @JsonProperty @Schema(required = true) private final String downloadUrl;
    @JsonProperty @Schema(required = true) private final String md5;
    @JsonProperty @Schema(required = true) private final String sha1;

    public Artifact(
        @Schema(required = true) final
        String variant,
        final Group group,
        @Schema(required = true) final
        String artifactId,
        @Schema(required = true) final
        String version,
        @Schema(required = true) final
        String downloadUrl,
        @Schema(required = true) final
        String md5,
        @Schema(required = true) final
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
    public boolean equals(final Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        final var that = (Artifact) obj;
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
