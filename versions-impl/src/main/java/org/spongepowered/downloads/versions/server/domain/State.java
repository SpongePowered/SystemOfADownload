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
package org.spongepowered.downloads.versions.server.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.CompressedJsonable;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.SortedMap;
import io.vavr.collection.TreeMap;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.tags.ArtifactTagEntry;
import org.spongepowered.downloads.versions.api.models.tags.ArtifactTagValue;

import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public interface State {
    boolean isRegistered();

    static Empty empty() {
        return new Empty();
    }

    final record Empty() implements State {
        @Override
        public boolean isRegistered() {
            return false;
        }

        public ACState register(ACEvent.ArtifactCoordinatesUpdated event) {
            return new ACState(event.coordinates());
        }
    }

    @JsonDeserialize
    final record ACState(
        ArtifactCoordinates coordinates,
        SortedMap<String, ArtifactTagValue> collection,
        Map<String, List<Artifact>> versionedArtifacts,
        boolean unregistered,
        Map<String, ArtifactTagEntry> tags,
        String promotionRegex,
        boolean manualPromotionAllowed
    ) implements CompressedJsonable, State {

        ACState(ArtifactCoordinates coordinates) {
            this(coordinates, TreeMap.empty(), HashMap.empty(), false, HashMap.empty(), "", false);
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
                this.versionedArtifacts,
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
                this.versionedArtifacts,
                this.unregistered,
                tagMap,
                "",
                false
            );
        }

        private Function<String, ArtifactTagValue> convertArtifactVersionToTagValues(
            ACState state, Map<String, ArtifactTagEntry> tagMap
        ) {
            return version -> {
                final var mavenCoordinates = state.coordinates.version(version);
                final Map<String, String> tagValues = tagMap.mapValues(
                    tag -> tag.generateValue(mavenCoordinates).tagValue());
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
                this.versionedArtifacts,
                false,
                this.tags,
                regex,
                enableManualPromotion
            );
        }

        public ACState withAddedArtifacts(MavenCoordinates coordinates, List<Artifact> newArtifacts) {
            final var existing = this.versionedArtifacts.get(coordinates.version)
                .getOrElse(List::empty);
            final var existingArtifactsByClassifier = existing.toMap(a -> a.classifier().orElse(""), Function.identity());
            final var newArtifactList = existing.appendAll(
                newArtifacts.filter(Predicate.not(artifact -> existingArtifactsByClassifier.containsKey(artifact.classifier().orElse("")))));
            final var versionedArtifacts = this.versionedArtifacts.put(coordinates.version, newArtifactList);
            return new ACState(
                this.coordinates,
                this.collection,
                versionedArtifacts,
                false,
                this.tags,
                this.promotionRegex,
                this.manualPromotionAllowed
            );
        }

        public java.util.List<ACEvent> addVersion(MavenCoordinates coordinates) {
            final var versions = this.collection
                .keySet()
                .toSortedSet(Comparator.comparing(ComparableVersion::new));
            final var newIndex = versions
                .add(coordinates.version)
                .toList()
                .indexOf(coordinates.version);
            final var versionRegistered = new ACEvent.ArtifactVersionRegistered(coordinates, newIndex);
            final var events = List.<ACEvent>empty();
            if (newIndex >= versions.size()) {
                return events.append(versionRegistered).toJavaList();
            }
            final var strings = versions.toList().subSequence(newIndex);
            final var versionMoved = new ACEvent.ArtifactVersionMoved(coordinates, newIndex, strings.map(this.coordinates::version));
            return events.append(versionRegistered).append(versionMoved).toJavaList();
        }
    }
}
