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
package org.spongepowered.downloads.versions.collection;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.CompressedJsonable;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.collection.SortedMap;
import io.vavr.collection.TreeMap;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.versions.api.models.tags.ArtifactTagEntry;
import org.spongepowered.downloads.versions.api.models.tags.ArtifactTagValue;

import java.util.Comparator;
import java.util.Objects;
import java.util.StringJoiner;

@JsonDeserialize
public record ACState(ArtifactCoordinates coordinates,
                      SortedMap<String, ArtifactTagValue> collection,
                      boolean unregistered,
                      Map<String, ArtifactTagEntry> tags) implements CompressedJsonable {

    public static ACState empty() {
        return new ACState(
            new ArtifactCoordinates("com.example", "example"),
            TreeMap.empty(Comparator.comparing(ComparableVersion::new)),
            true,
            HashMap.empty()
        );
    }

    public boolean isRegistered() {
        return !this.unregistered;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ACState acState = (ACState) o;
        return Objects.equals(coordinates, acState.coordinates) && Objects.equals(
            collection, acState.collection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(coordinates, collection);
    }

    @Override
    public String toString() {
        return new StringJoiner(
            ", ", ACState.class.getSimpleName() + "[", "]")
            .add("coordinates=" + coordinates)
            .add("collection=" + collection)
            .toString();
    }
}
