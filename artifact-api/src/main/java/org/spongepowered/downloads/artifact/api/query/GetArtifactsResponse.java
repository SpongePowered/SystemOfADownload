package org.spongepowered.downloads.artifact.api.query;

import io.vavr.collection.List;

import java.util.Objects;

public interface GetArtifactsResponse {

    final static class GroupMissing implements GetArtifactsResponse {
        private final String groupRequested;

        public GroupMissing(
            final String groupRequested
        ) {
            this.groupRequested = groupRequested;
        }

        public String groupRequested() {
            return this.groupRequested;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (GroupMissing) obj;
            return Objects.equals(this.groupRequested, that.groupRequested);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.groupRequested);
        }

        @Override
        public String toString() {
            return "GroupMissing[" +
                "groupRequested=" + this.groupRequested + ']';
        }

    }

    final static class ArtifactsAvailable implements GetArtifactsResponse {
        private final List<String> artifactIds;

        public ArtifactsAvailable(
            final List<String> artifactIds
        ) {
            this.artifactIds = artifactIds;
        }

        public List<String> artifactIds() {
            return this.artifactIds;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (ArtifactsAvailable) obj;
            return Objects.equals(this.artifactIds, that.artifactIds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.artifactIds);
        }

        @Override
        public String toString() {
            return "ArtifactsAvailable[" +
                "artifactIds=" + this.artifactIds + ']';
        }

    }
}
