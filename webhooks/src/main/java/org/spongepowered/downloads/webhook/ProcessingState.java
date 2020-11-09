package org.spongepowered.downloads.webhook;

import io.vavr.Tuple2;
import io.vavr.collection.Map;
import org.spongepowered.downloads.git.api.CommitSha;

import java.util.Optional;

sealed interface ProcessingState {

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

    final record EmptyState() implements ProcessingState {

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
    }

    final record MetadataState(
        String coordinates,
        String repository,
        Map<String, Tuple2<String, String>> artifacts
    ) implements ProcessingState {

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
    }

    final record CommittedState(String s, String repository, Map<String, Tuple2<String, String>> artifacts, CommitSha commit)
        implements ProcessingState {
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
    }

}
