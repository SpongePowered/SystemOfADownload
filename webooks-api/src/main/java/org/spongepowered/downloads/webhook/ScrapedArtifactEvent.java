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
package org.spongepowered.downloads.webhook;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.Tuple2;
import io.vavr.collection.Map;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.git.api.CommitSha;

import java.io.Serial;
import java.util.Objects;
import java.util.StringJoiner;

@JsonSubTypes({
    @JsonSubTypes.Type(value = ScrapedArtifactEvent.InitializeArtifactForProcessing.class, name = "Initialize"),
    @JsonSubTypes.Type(value = ScrapedArtifactEvent.ArtifactRequested.class, name = "Requested"),
    @JsonSubTypes.Type(value = ScrapedArtifactEvent.AssociatedMavenMetadata.class, name = "AssociatedMetadata"),
    @JsonSubTypes.Type(value = ScrapedArtifactEvent.AssociateCommitSha.class, name = "AssociatedCommit")
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
public interface ScrapedArtifactEvent extends AggregateEvent<ScrapedArtifactEvent>, Jsonable {

    AggregateEventShards<ScrapedArtifactEvent> INSTANCE = AggregateEventTag.sharded(ScrapedArtifactEvent.class, 1);

    @Override
    default AggregateEventTagger<ScrapedArtifactEvent> aggregateTag() {
        return INSTANCE;
    }

    String mavenCoordinates();

    @JsonDeserialize
    final class InitializeArtifactForProcessing implements ScrapedArtifactEvent {
        @Serial private static final long serialVersionUID = 0L;
        @JsonProperty public final MavenCoordinates coordinates;
        @JsonProperty public final String repository;
        @JsonProperty public final String componentId;

        @JsonCreator
        public InitializeArtifactForProcessing(
            final MavenCoordinates coordinates,
            final String repository,
            final String componentId
        ) {
            this.coordinates = coordinates;
            this.repository = repository;
            this.componentId = componentId;
        }

        public String mavenCoordinates() {
            return this.coordinates.toString();
        }

        public String repository() {
            return this.repository;
        }

        public String componentId() {
            return this.componentId;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (InitializeArtifactForProcessing) obj;
            return Objects.equals(this.coordinates, that.coordinates) &&
                Objects.equals(this.repository, that.repository) &&
                Objects.equals(this.componentId, that.componentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.coordinates, this.repository, this.componentId);
        }

        @Override
        public String toString() {
            return "InitializeArtifactForProcessing[" +
                "mavenCoordinates=" + this.coordinates + ", " +
                "repository=" + this.repository + ", " +
                "componentId=" + this.componentId + ']';
        }

    }
    @JsonDeserialize
    final class ArtifactRequested implements ScrapedArtifactEvent {
        @Serial private static final long serialVersionUID = 0L;
        public final MavenCoordinates coordinates;

        @JsonCreator
        public ArtifactRequested(
            final MavenCoordinates coordinates
        ) {
            this.coordinates = coordinates;
        }

        public String mavenGroupId() {
            return this.coordinates.groupId;
        }

        public String mavenArtifactId() {
            return this.coordinates.artifactId;
        }

        public String componentVersion() {
            return this.coordinates.version;
        }

        public String mavenCoordinates() {
            return this.coordinates.asStandardCoordinates();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || this.getClass() != o.getClass()) {
                return false;
            }
            final ArtifactRequested that = (ArtifactRequested) o;
            return Objects.equals(this.coordinates, that.coordinates);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.coordinates);
        }

        @Override
        public String toString() {
            return new StringJoiner(
                ", ", ArtifactRequested.class.getSimpleName() + "[",
                "]"
            )
                .add("coordinates=" + this.coordinates)
                .toString();
        }
    }
    @JsonDeserialize
    final class AssociatedMavenMetadata
        implements ScrapedArtifactEvent {
        @Serial private static final long serialVersionUID = 0L;
        private final ArtifactCollection collection;
        private final String mavenCoordinates;
        private final String tagVersion;
        private final Map<String, Tuple2<String, String>> artifactPathToSonatypeId;

        @JsonCreator
        public AssociatedMavenMetadata(
            final ArtifactCollection collection,
            final String mavenCoordinates,
            final String tagVersion, final Map<String, Tuple2<String, String>> artifactPathToSonatypeId
        ) {
            this.collection = collection;
            this.mavenCoordinates = mavenCoordinates;
            this.tagVersion = tagVersion;
            this.artifactPathToSonatypeId = artifactPathToSonatypeId;
        }

        public ArtifactCollection collection() {
            return this.collection;
        }

        public String mavenCoordinates() {
            return this.mavenCoordinates;
        }

        public String tagVersion() {
            return this.tagVersion;
        }

        public Map<String, Tuple2<String, String>> artifactPathToSonatypeId() {
            return this.artifactPathToSonatypeId;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (AssociatedMavenMetadata) obj;
            return Objects.equals(this.collection, that.collection) &&
                Objects.equals(this.mavenCoordinates, that.mavenCoordinates) &&
                Objects.equals(this.tagVersion, that.tagVersion) &&
                Objects.equals(this.artifactPathToSonatypeId, that.artifactPathToSonatypeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.collection, this.mavenCoordinates, this.tagVersion, this.artifactPathToSonatypeId);
        }

        @Override
        public String toString() {
            return "AssociatedMavenMetadata[" +
                "collection=" + this.collection + ", " +
                "mavenCoordinates=" + this.mavenCoordinates + ", " +
                "tagVersion=" + this.tagVersion + ", " +
                "artifactPathToSonatypeId=" + this.artifactPathToSonatypeId + ']';
        }

    }
    @JsonDeserialize
    final class AssociateCommitSha implements ScrapedArtifactEvent {
        @Serial private static final long serialVersionUID = 0L;
        private final ArtifactCollection collection;
        private final CommitSha commit;

        @JsonCreator
        public AssociateCommitSha(
            final ArtifactCollection collection,
            final CommitSha commit
        ) {
            this.collection = collection;
            this.commit = commit;
        }

        public ArtifactCollection collection() {
            return this.collection;
        }

        public String mavenCoordinates() {
            return this.collection.coordinates.toString();
        }

        public String groupId() {
            return this.collection.coordinates.groupId;
        }

        public String artifactId() {
            return this.collection.coordinates.artifactId;
        }

        public String version() {
            return this.collection.coordinates.version;
        }

        public CommitSha commit() {
            return this.commit;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (AssociateCommitSha) obj;
            return Objects.equals(this.collection, that.collection) &&
                Objects.equals(this.commit, that.commit);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                this.collection, this.commit);
        }

        @Override
        public String toString() {
            return "AssociateCommitSha[" +
                "collection=" + this.collection + ", " +
                "commit=" + this.commit + ']';
        }


    }
}
