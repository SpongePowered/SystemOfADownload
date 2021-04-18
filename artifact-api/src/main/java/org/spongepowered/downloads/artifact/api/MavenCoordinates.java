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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Objects;
import java.util.StringJoiner;
import java.util.regex.Pattern;

@JsonDeserialize
public final class MavenCoordinates {

    private static final Pattern MAVEN_REGEX = Pattern.compile("[\\w.]+");

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
    /**
     * The version of an artifact, as defined by the Apache Maven documentation. This is
     * traditionally specified as a Maven repository searchable version string, such as
     * {@code 1.0.0-SNAPSHOT}.
     * See <a href="https://maven.apache.org/pom.html#Maven_Coordinates">Maven Coordinates</a>.
     */
    @JsonProperty(required = true)
    public final String version;

    @JsonIgnore
    public final VersionType versionType;

    /**
     * Parses a set of maven formatted coordinates as per
     * <a href="https://maven.apache.org/pom.html#Maven_Coordinates">Apache
     * Maven's documentation</a>.
     *
     * @param coordinates The coordinates delimited by `:`
     * @return A parsed set of MavenCoordinates
     */
    public static MavenCoordinates parse(final String coordinates) {
        final var splitCoordinates = coordinates.split(":");
        if (splitCoordinates.length < 3) {
            throw new IllegalArgumentException(
                "Coordinates are not formatted or delimited by the `:` character or contains fewer than the required size");
        }
        final var groupId = splitCoordinates[0];
        if (!MAVEN_REGEX.asMatchPredicate().test(groupId)) {
            throw new IllegalArgumentException("GroupId does not conform to regex rules for a maven group id");
        }
        final var artifactId = splitCoordinates[1];
        if (!MAVEN_REGEX.asMatchPredicate().test(artifactId)) {
            throw new IllegalArgumentException("ArtifactId does not conform to regex rules for a maven artifact id");
        }
        final var version = splitCoordinates[2];

        VersionType.fromVersion(version); // validates the version is going to be valid somewhat
        return new MavenCoordinates(groupId, artifactId, version);
    }

    public MavenCoordinates(String coordinates) {
        final var splitCoordinates = coordinates.split(":");
        if (splitCoordinates.length < 3) {
            throw new IllegalArgumentException(
                "Coordinates are not formatted or delimited by the `:` character or contains fewer than the required size");
        }
        final var groupId = splitCoordinates[0];
        if (!MAVEN_REGEX.asMatchPredicate().test(groupId)) {
            throw new IllegalArgumentException("GroupId does not conform to regex rules for a maven group id");
        }
        final var artifactId = splitCoordinates[1];
        if (!MAVEN_REGEX.asMatchPredicate().test(artifactId)) {
            throw new IllegalArgumentException("ArtifactId does not conform to regex rules for a maven artifact id");
        }
        final var version = splitCoordinates[2];

        VersionType.fromVersion(version); // validates the version is going to be valid somewhat
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.versionType = VersionType.fromVersion(version);
    }

    @JsonCreator
    public MavenCoordinates(
        @JsonProperty("groupId") final String groupId,
        @JsonProperty("artifactId") final String artifactId,
        @JsonProperty("version") final String version
    ) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.versionType = VersionType.fromVersion(version);
    }

    @JsonIgnore
    public String asStandardCoordinates() {
        return new StringJoiner(":")
            .add(this.groupId)
            .add(this.artifactId)
            .add(this.versionType.asStandardVersionString(this.version))
            .toString();
    }

    @JsonIgnore
    public boolean isSnapshot() {
        return this.versionType.isSnapshot();
    }

    @Override
    public String toString() {
        return new StringJoiner(":")
            .add(this.groupId)
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
        final MavenCoordinates that = (MavenCoordinates) o;
        return Objects.equals(this.groupId, that.groupId) && Objects.equals(
            this.artifactId, that.artifactId) && Objects.equals(this.version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.groupId, this.artifactId, this.version);
    }
}
