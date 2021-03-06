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
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.collection.SortedMap;
import io.vavr.collection.TreeMap;
import io.vavr.control.Try;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.versions.api.models.tags.ArtifactTagEntry;
import org.spongepowered.downloads.versions.api.models.tags.ArtifactTagValue;

import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.regex.Pattern;

@JsonDeserialize
public record ACState(
    ArtifactCoordinates coordinates,
    SortedMap<String, ArtifactTagValue> collection,
    boolean unregistered,
    Map<String, ArtifactTagEntry> tags,
    String promotionRegex,
    boolean manualPromotionAllowed
) implements CompressedJsonable {

    public static ACState empty() {
        return new ACState(
            new ArtifactCoordinates("com.example", "example"),
            TreeMap.empty(Comparator.comparing(ComparableVersion::new).reversed()),
            true,
            HashMap.empty(),
            "",
            false
        );
    }

    public boolean isRegistered() {
        return !this.unregistered;
    }

    public ACState withVersion(String version) {
        final var versionMap = this.collection
            .computeIfAbsent(version, convertArtifactVersionToTagValues(this, this.tags))
            ._2
            .toSortedMap(Comparator.comparing(ComparableVersion::new).reversed(), Tuple2::_1, Tuple2::_2);
        return new ACState(
            this.coordinates,
            versionMap,
            this.unregistered,
            this.tags,
            "",
            false
        );
    }

    public ACState withTag(ArtifactTagEntry entry) {
        final var tagMap = this.tags().put(entry.name().toLowerCase(Locale.ROOT), entry);
        final var versionedTags = this.collection
            .replaceAll((version, values) -> convertArtifactVersionToTagValues(this, tagMap).apply(version));
        return new ACState(
            this.coordinates,
            versionedTags,
            this.unregistered,
            tagMap,
            "",
            false
        );
    }

    private Function<String, ArtifactTagValue> convertArtifactVersionToTagValues(
        ACState state, Map<String, org.spongepowered.downloads.versions.api.models.tags.ArtifactTagEntry> tagMap
    ) {
        return version -> {
            final Map<String, String> tagValues = tagMap.mapValues(tag -> {
                final var expectedGroup = tag.matchingGroup();
                final var matcher = Pattern.compile(tag.regex()).matcher(version);
                if (matcher.find()) {
                    return Try.of(() -> matcher.group(expectedGroup))
                        .getOrElse("");
                }
                return "";
            });
            return new ArtifactTagValue(state.coordinates().version(version), tagValues, false);
        };
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

    public ACState withPromotionDetails(String regex, boolean enableManualPromotion) {
        final var pattern = Pattern.compile(regex);
        final var versionedTags = this.collection
            .replaceAll((version, value) -> value.promote(pattern.matcher(version).find()))
            .toSortedMap(Comparator.comparing(ComparableVersion::new).reversed(), Tuple2::_1, Tuple2::_2);
        return new ACState(
            this.coordinates,
            versionedTags,
            false,
            this.tags,
            regex,
            enableManualPromotion
        );
    }

    public ACState withCoordinates(ArtifactCoordinates coordinates) {
        return new ACState(
            coordinates,
            this.collection,
            false,
            this.tags,
            "",
            false
        );
    }
}
