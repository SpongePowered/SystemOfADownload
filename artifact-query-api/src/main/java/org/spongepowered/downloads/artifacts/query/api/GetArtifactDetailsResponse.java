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
package org.spongepowered.downloads.artifacts.query.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.vavr.collection.Map;
import io.vavr.collection.SortedSet;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

import java.util.Objects;
import java.util.StringJoiner;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = GetArtifactDetailsResponse.RetrievedArtifact.class,
        name = "latest")
})
public interface GetArtifactDetailsResponse {

    @JsonSerialize
    record RetrievedArtifact(ArtifactCoordinates coordinates,
                             String displayName, String website, String gitRepository,
                             String issues,
                             Map<String, SortedSet<String>> tags) implements GetArtifactDetailsResponse {

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
}
