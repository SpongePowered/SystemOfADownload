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
    @JsonProperty
    @Schema(required = true)
    public final String variant;
    @JsonIgnore public final MavenCoordinates coordinates;
    @JsonProperty
    @Schema(required = true)
    public final String downloadUrl;
    @JsonProperty
    @Schema(required = true)
    public final String md5;
    @JsonProperty
    @Schema(required = true)
    public final String sha1;

    public Artifact(
        final String variant,
        final MavenCoordinates coordinates,
        final String downloadUrl, final String md5, final String sha1
    ) {
        this.variant = variant;
        this.coordinates = coordinates;
        this.downloadUrl = downloadUrl;
        this.md5 = md5;
        this.sha1 = sha1;
    }

    /**
     * Gets the
     * @param delimiter
     * @return
     */
    public String getFormattedString(final String delimiter) {
        if (Objects.requireNonNull(delimiter, "Delimiter cannot be null!").isEmpty()) {
            throw new IllegalArgumentException("Delimiter cannot be an empty string!");
        }
        final StringJoiner stringJoiner = new StringJoiner(delimiter);
        return stringJoiner
            .add(this.coordinates.groupId)
            .add(this.coordinates.artifactId)
            .add(this.coordinates.version)
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
        return Objects.equals(this.variant, artifact.variant) && Objects.equals(
            this.coordinates, artifact.coordinates) && Objects.equals(
            this.downloadUrl, artifact.downloadUrl) && Objects.equals(
            this.md5, artifact.md5) && Objects.equals(this.sha1, artifact.sha1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.variant, this.coordinates, this.downloadUrl, this.md5, this.sha1);
    }

    @Override
    public String toString() {
        return new StringJoiner(
            ", ", Artifact.class.getSimpleName() + "[", "]")
            .add("coordinates=" + this.coordinates)
            .add("variant='" + this.variant + "'")
            .add("downloadUrl='" + this.downloadUrl + "'")
            .add("md5='" + this.md5 + "'")
            .add("sha1='" + this.sha1 + "'")
            .toString();
    }
}
