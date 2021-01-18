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
package org.spongepowered.downloads.artifact.collection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.Group;

import java.io.Serial;
import java.util.Objects;

public interface ACEvent extends Jsonable, AggregateEvent<ACEvent> {
    AggregateEventShards<ACEvent> INSTANCE = AggregateEventTag.sharded(ACEvent.class, 10);

    @Override
    default AggregateEventTagger<ACEvent> aggregateTag() {
        return INSTANCE;
    }

    final class ArtifactGroupUpdated implements ACEvent {
        @Serial private static final long serialVersionUID = 0L;
        public final Group groupId;

        @JsonCreator
        public ArtifactGroupUpdated(final Group groupId) {
            this.groupId = groupId;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final var that = (ArtifactGroupUpdated) obj;
            return Objects.equals(this.groupId, that.groupId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.groupId);
        }

        @Override
        public String toString() {
            return "ArtifactGroupUpdated[" +
                "groupId=" + this.groupId + ']';
        }
    }

    final class ArtifactIdUpdated implements ACEvent {
        @Serial private static final long serialVersionUID = 0L;
        public final String artifactId;

        @JsonCreator
        public ArtifactIdUpdated(final String artifactId) {
            this.artifactId = artifactId;
        }

        public String artifactId() {
            return this.artifactId;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final var that = (ArtifactIdUpdated) obj;
            return Objects.equals(this.artifactId, that.artifactId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.artifactId);
        }

        @Override
        public String toString() {
            return "ArtifactIdUpdated[" +
                "artifactId=" + this.artifactId + ']';
        }
    }

    final class ArtifactVersionRegistered implements ACEvent {
        @Serial private static final long serialVersionUID = 0L;
        public final String version;
        public final ArtifactCollection collection;

        @JsonCreator
        public ArtifactVersionRegistered(final String version, final ArtifactCollection collection) {
            this.version = version;
            this.collection = collection;
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
            return Objects.equals(this.version, that.version) &&
                Objects.equals(this.collection, that.collection);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.version, this.collection);
        }

        @Override
        public String toString() {
            return "ArtifactVersionRegistered[" +
                "version=" + this.version + ", " +
                "collection=" + this.collection + ']';
        }
    }

    final class CollectionRegistered implements ACEvent {
        @Serial private static final long serialVersionUID = 0L;
        public final ArtifactCollection collection;

        public CollectionRegistered(final ArtifactCollection collection) {
            this.collection = collection;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final var that = (CollectionRegistered) obj;
            return Objects.equals(this.collection, that.collection);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.collection);
        }

        @Override
        public String toString() {
            return "CollectionRegistered[" +
                "collection=" + this.collection + ']';
        }
    }
}
