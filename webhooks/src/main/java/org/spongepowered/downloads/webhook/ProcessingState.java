package org.spongepowered.downloads.webhook;

import io.vavr.Tuple2;
import io.vavr.collection.Map;
import org.spongepowered.downloads.git.api.CommitSha;

import java.util.Objects;
import java.util.Optional;

interface ProcessingState {

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

    final static class EmptyState implements ProcessingState {
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

    final static class MetadataState implements ProcessingState {
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
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
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

    final static class CommittedState
        implements ProcessingState {
        private final String s;
        private final String repository;
        private final Map<String, Tuple2<String, String>> artifacts;
        private final CommitSha commit;

        public CommittedState(
            final String s, final String repository, final Map<String, Tuple2<String, String>> artifacts, final CommitSha commit
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
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
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
