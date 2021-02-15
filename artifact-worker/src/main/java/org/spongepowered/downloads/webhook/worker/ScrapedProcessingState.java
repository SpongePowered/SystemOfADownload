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
package org.spongepowered.downloads.webhook.worker;

import io.vavr.Tuple2;
import io.vavr.collection.Map;
import org.spongepowered.downloads.git.api.CommitSha;

import java.util.Objects;
import java.util.Optional;

interface ScrapedProcessingState {

    boolean hasStarted();

    boolean hasMetadata();

    boolean hasCommit();

    boolean hasCompleted();

    Optional<String> getCoordinates();

    Optional<String> getRepository();

    default Optional<String> getArtifactId() {
        return this.getCoordinates().map(coords -> coords.split(":")[1]);
    }

    default Optional<String> getGroupId() {
        return this.getCoordinates().map(coords -> coords.split(":")[0]);
    }

    default Optional<String> getMavenVersion() {
        return this.getCoordinates().map(coords -> coords.split(":")[2]);
    }

    Optional<Map<String, Tuple2<String, String>>> getArtifacts();

    static final class EmptyState implements ScrapedProcessingState {
        public EmptyState() {
        }

        public static EmptyState empty() {
            return new EmptyState();
        }

        @Override
        public boolean hasStarted() {
            return false;
        }

        @Override
        public boolean hasMetadata() {
            return false;
        }

        @Override
        public boolean hasCommit() {
            return false;
        }

        @Override
        public boolean hasCompleted() {
            return false;
        }

        @Override
        public Optional<String> getCoordinates() {
            return Optional.empty();
        }

        @Override
        public Optional<String> getRepository() {
            return Optional.empty();
        }

        @Override
        public Optional<Map<String, Tuple2<String, String>>> getArtifacts() {
            return Optional.empty();
        }

        @Override
        public boolean equals(final Object obj) {
            return obj == this || obj != null && obj.getClass() == this.getClass();
        }

        @Override
        public int hashCode() {
            return 1;
        }

        @Override
        public String toString() {
            return "EmptyState[]";
        }

    }

    static final class MetadataState implements ScrapedProcessingState {
        private final String coordinates;
        private final String repository;
        private final Map<String, Tuple2<String, String>> artifacts;

        public MetadataState(
            final String coordinates,
            final String repository,
            final Map<String, Tuple2<String, String>> artifacts
        ) {
            this.coordinates = coordinates;
            this.repository = repository;
            this.artifacts = artifacts;
        }

        @Override
        public boolean hasStarted() {
            return true;
        }

        @Override
        public boolean hasMetadata() {
            return true;
        }

        @Override
        public boolean hasCommit() {
            return false;
        }

        @Override
        public boolean hasCompleted() {
            return false;
        }

        @Override
        public Optional<String> getRepository() {
            return Optional.of(this.repository());
        }

        @Override
        public Optional<String> getCoordinates() {
            return Optional.of(this.coordinates);
        }

        @Override
        public Optional<Map<String, Tuple2<String, String>>> getArtifacts() {
            return Optional.of(this.artifacts);
        }

        public String coordinates() {
            return this.coordinates;
        }

        public String repository() {
            return this.repository;
        }

        public Map<String, Tuple2<String, String>> artifacts() {
            return this.artifacts;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final var that = (MetadataState) obj;
            return Objects.equals(this.coordinates, that.coordinates) &&
                Objects.equals(this.repository, that.repository) &&
                Objects.equals(this.artifacts, that.artifacts);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.coordinates, this.repository, this.artifacts);
        }

        @Override
        public String toString() {
            return "MetadataState[" +
                "coordinates=" + this.coordinates + ", " +
                "repository=" + this.repository + ", " +
                "artifacts=" + this.artifacts + ']';
        }

    }

    static final class CommittedState
        implements ScrapedProcessingState {
        private final String s;
        private final String repository;
        private final Map<String, Tuple2<String, String>> artifacts;
        private final CommitSha commit;

        public CommittedState(
            final String s, final String repository, final Map<String, Tuple2<String, String>> artifacts,
            final CommitSha commit
        ) {
            this.s = s;
            this.repository = repository;
            this.artifacts = artifacts;
            this.commit = commit;
        }

        @Override
        public boolean hasStarted() {
            return true;
        }

        @Override
        public boolean hasMetadata() {
            return true;
        }

        @Override
        public boolean hasCommit() {
            return true;
        }

        @Override
        public boolean hasCompleted() {
            return false;
        }

        @Override
        public Optional<String> getCoordinates() {
            return Optional.empty();
        }

        @Override
        public Optional<String> getRepository() {
            return Optional.of(this.repository());
        }

        @Override
        public Optional<Map<String, Tuple2<String, String>>> getArtifacts() {
            return Optional.of(this.artifacts);
        }

        public String s() {
            return this.s;
        }

        public String repository() {
            return this.repository;
        }

        public Map<String, Tuple2<String, String>> artifacts() {
            return this.artifacts;
        }

        public CommitSha commit() {
            return this.commit;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final var that = (CommittedState) obj;
            return Objects.equals(this.s, that.s) &&
                Objects.equals(this.repository, that.repository) &&
                Objects.equals(this.artifacts, that.artifacts) &&
                Objects.equals(this.commit, that.commit);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.s, this.repository, this.artifacts, this.commit);
        }

        @Override
        public String toString() {
            return "CommittedState[" +
                "s=" + this.s + ", " +
                "repository=" + this.repository + ", " +
                "artifacts=" + this.artifacts + ", " +
                "commit=" + this.commit + ']';
        }

    }

}
