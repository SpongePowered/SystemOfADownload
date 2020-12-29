package org.spongepowered.downloads.artifact.api.query;

import io.vavr.collection.Map;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;

import java.util.Objects;

public interface GetVersionsResponse {

    final static class VersionsAvailable implements GetVersionsResponse {
        private final Map<String, ArtifactCollection> artifacts;

        public VersionsAvailable(final Map<String, ArtifactCollection> artifacts) {
            this.artifacts = artifacts;
        }

        public Map<String, ArtifactCollection> artifacts() {
            return this.artifacts;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (VersionsAvailable) obj;
            return Objects.equals(this.artifacts, that.artifacts);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.artifacts);
        }

        @Override
        public String toString() {
            return "VersionsAvailable[" +
                "artifacts=" + this.artifacts + ']';
        }
    }

    final static class GroupUnknown implements GetVersionsResponse {
        private final String groupId;

        public GroupUnknown(final String groupId) {
            this.groupId = groupId;
        }

        public String groupId() {
            return this.groupId;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (GroupUnknown) obj;
            return Objects.equals(this.groupId, that.groupId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.groupId);
        }

        @Override
        public String toString() {
            return "GroupUnknown[" +
                "groupId=" + this.groupId + ']';
        }
    }

    final static class ArtifactUnknown implements GetVersionsResponse {
        private final String artifactId;

        public ArtifactUnknown(final String artifactId) {
            this.artifactId = artifactId;
        }

        public String artifactId() {
            return this.artifactId;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (ArtifactUnknown) obj;
            return Objects.equals(this.artifactId, that.artifactId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.artifactId);
        }

        @Override
        public String toString() {
            return "ArtifactUnknown[" +
                "artifactId=" + this.artifactId + ']';
        }
    }
}
