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
package org.spongepowered.downloads.artifact.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

import java.util.Objects;
import java.util.StringJoiner;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
    @JsonSubTypes.Type(value = GetArtifactDetailsResponse.GroupMissing.class, name = "UnknownGroup"),
    @JsonSubTypes.Type(value = GetArtifactDetailsResponse.ArtifactMissing.class, name = "UnknownArtifact"),
    @JsonSubTypes.Type(value = GetArtifactDetailsResponse.RetrievedArtifact.class, name = "Artifact"),
})
public interface GetArtifactDetailsResponse {

    @JsonSerialize
    final class RetrievedArtifact implements GetArtifactDetailsResponse {
        public final ArtifactCoordinates coordinates;
        public final String displayName;
        public final String website;
        public final String gitRepository;
        public final String issues;

        public RetrievedArtifact(
            final ArtifactCoordinates coordinates, final String displayName, final String website,
            final String gitRepository,
            final String issues
        ) {
            this.coordinates = coordinates;
            this.displayName = displayName;
            this.website = website;
            this.gitRepository = gitRepository;
            this.issues = issues;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RetrievedArtifact that = (RetrievedArtifact) o;
            return Objects.equals(coordinates, that.coordinates) && Objects.equals(
                displayName, that.displayName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(coordinates, displayName);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", RetrievedArtifact.class.getSimpleName() + "[", "]")
                .add("coordinates=" + coordinates)
                .add("displayName='" + displayName + "'")
                .toString();
        }
    }

    @JsonSerialize
    final class GroupMissing implements GetArtifactDetailsResponse {

        @JsonProperty
        private final String groupRequested;

        @JsonCreator
        public GroupMissing(
            final String groupRequested
        ) {
            this.groupRequested = groupRequested;
        }

        public String groupRequested() {
            return this.groupRequested;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (GroupMissing) obj;
            return Objects.equals(this.groupRequested, that.groupRequested);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.groupRequested);
        }

        @Override
        public String toString() {
            return "GroupMissing[" +
                "groupRequested=" + this.groupRequested + ']';
        }

    }

    @JsonSerialize
    final class ArtifactMissing implements GetArtifactDetailsResponse {

        private final String artifactRequested;

        @JsonCreator
        public ArtifactMissing(final String artifactRequested) {
            this.artifactRequested = artifactRequested;
        }

        public String artifactRequested() {
            return this.artifactRequested;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ArtifactMissing that = (ArtifactMissing) o;
            return Objects.equals(artifactRequested, that.artifactRequested);
        }

        @Override
        public int hashCode() {
            return Objects.hash(artifactRequested);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", ArtifactMissing.class.getSimpleName() + "[", "]")
                .add("artifactRequested='" + artifactRequested + "'")
                .toString();
        }
    }
}
