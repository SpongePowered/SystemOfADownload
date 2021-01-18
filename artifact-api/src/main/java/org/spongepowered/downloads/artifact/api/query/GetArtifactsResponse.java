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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.vavr.collection.List;

import java.util.Objects;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
    @JsonSubTypes.Type(value = GetArtifactsResponse.GroupMissing.class, name = "UnknownGroup"),
    @JsonSubTypes.Type(value = GetArtifactsResponse.ArtifactsAvailable.class, name = "Artifacts"),
})
public interface GetArtifactsResponse {

    @JsonSerialize
    final class GroupMissing implements GetArtifactsResponse {

        @JsonProperty
        private final String groupRequested;

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
    final class ArtifactsAvailable implements GetArtifactsResponse {

        @JsonProperty
        private final List<String> artifactIds;

        public ArtifactsAvailable(
            final List<String> artifactIds
        ) {
            this.artifactIds = artifactIds;
        }

        public List<String> artifactIds() {
            return this.artifactIds;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (ArtifactsAvailable) obj;
            return Objects.equals(this.artifactIds, that.artifactIds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.artifactIds);
        }

        @Override
        public String toString() {
            return "ArtifactsAvailable[" +
                "artifactIds=" + this.artifactIds + ']';
        }

    }
}
