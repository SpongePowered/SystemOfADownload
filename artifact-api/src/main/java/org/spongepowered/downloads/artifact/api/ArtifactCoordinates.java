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

    public String asMavenString() {
        return this.groupId + ":" + this.artifactId;
    }
}
