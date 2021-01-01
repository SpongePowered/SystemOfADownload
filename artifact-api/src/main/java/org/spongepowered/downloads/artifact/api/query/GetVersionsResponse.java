package org.spongepowered.downloads.artifact.api.query;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.vavr.collection.Map;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;

import java.util.Objects;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
    @JsonSubTypes.Type(value = GetVersionsResponse.VersionsAvailable.class, name = "Artifacts"),
    @JsonSubTypes.Type(value = GetVersionsResponse.GroupUnknown.class, name = "UnknownGroup"),
    @JsonSubTypes.Type(value = GetVersionsResponse.ArtifactUnknown.class, name = "UnknownArtifact")
})
public interface GetVersionsResponse {

    @JsonSerialize
    final static class VersionsAvailable implements GetVersionsResponse {
        @JsonProperty
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

    @JsonSerialize
    final static class GroupUnknown implements GetVersionsResponse {
        @JsonProperty
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

    @JsonSerialize
    final static class ArtifactUnknown implements GetVersionsResponse {
        @JsonProperty
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
