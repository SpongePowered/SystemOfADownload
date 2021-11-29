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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.tags.ArtifactTagEntry;

import java.io.Serial;
import java.util.Objects;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
    @JsonSubTypes.Type(
        value = ACEvent.ArtifactTagRegistered.class,
        name = "tag-registered"
    ),
    @JsonSubTypes.Type(
        value = ACEvent.ArtifactCoordinatesUpdated.class,
        name = "updated-coordinates"
    ),
    @JsonSubTypes.Type(
        value = ACEvent.ArtifactVersionRegistered.class,
        name = "version-registered"
    ),
    @JsonSubTypes.Type(
        value = ACEvent.PromotionSettingModified.class,
        name = "promotion-settings-modified"
    ),
    @JsonSubTypes.Type(
        value = ACEvent.ArtifactVersionMoved.class,
        name = "versions-resorted"
    )
})
public interface ACEvent extends AggregateEvent<ACEvent>, Jsonable {
    AggregateEventShards<ACEvent> INSTANCE = AggregateEventTag.sharded(ACEvent.class, 10);

    @Override
    default AggregateEventTagger<ACEvent> aggregateTag() {
        return INSTANCE;
    }

    final class ArtifactCoordinatesUpdated implements ACEvent {
        @Serial private static final long serialVersionUID = 0L;
        public final ArtifactCoordinates coordinates;

        @JsonCreator
        public ArtifactCoordinatesUpdated(final ArtifactCoordinates coordinates) {
            this.coordinates = coordinates;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final var that = (ArtifactCoordinatesUpdated) obj;
            return Objects.equals(this.coordinates, that.coordinates);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.coordinates);
        }

        @Override
        public String toString() {
            return "ArtifactCoordinatesUpdated[" +
                "coordinates=" + this.coordinates + ']';
        }
    }

    record ArtifactVersionRegistered(
        MavenCoordinates version,
        int sorting
    ) implements ACEvent {
        @Serial private static final long serialVersionUID = 0L;

        @JsonCreator
        public ArtifactVersionRegistered {
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final var that = (ArtifactVersionRegistered) obj;
            return Objects.equals(this.version, that.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.version);
        }

        @Override
        public String toString() {
            return "ArtifactVersionRegistered[" +
                "version=" + this.version + ", ";
        }
    }

    @JsonDeserialize
    final record ArtifactTagRegistered(ArtifactCoordinates coordinates, @JsonProperty("entry") ArtifactTagEntry entry)
        implements ACEvent {

    }

    @JsonDeserialize
    final record PromotionSettingModified(ArtifactCoordinates coordinates, String regex, boolean enableManualPromotion)
        implements ACEvent {
    }

    @JsonDeserialize
    final record VersionedCollectionAdded(
        ArtifactCoordinates coordinates, ArtifactCollection collection,
        List<Artifact> newArtifacts
    ) implements ACEvent {
    }

    @JsonDeserialize
    final record ArtifactVersionMoved(
        MavenCoordinates coordinates,
        int newIndex,
        List<MavenCoordinates> reshuffled
    ) implements ACEvent{
    }
}
