package org.spongepowered.downloads.changelog;

import akka.NotUsed;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.changelog.api.query.ChangelogResponse;

import java.util.Objects;

public interface ChangelogCommand {

    @JsonDeserialize
    final static class RegisterArtifact
        implements ChangelogCommand, PersistentEntity.ReplyType<NotUsed> {
        private final Artifact artifact;

        public RegisterArtifact(Artifact artifact) {
            this.artifact = artifact;
        }

        public Artifact artifact() {
            return this.artifact;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (RegisterArtifact) obj;
            return Objects.equals(this.artifact, that.artifact);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.artifact);
        }

        @Override
        public String toString() {
            return "RegisterArtifact[" +
                "artifact=" + this.artifact + ']';
        }
    }

    @JsonDeserialize
    final static class GetChangelogFromCoordinates
        implements ChangelogCommand, PersistentEntity.ReplyType<ChangelogResponse> {
        private final String groupId;
        private final String artifactId;
        private final String version;

        public GetChangelogFromCoordinates(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }

        public String groupId() {
            return this.groupId;
        }

        public String artifactId() {
            return this.artifactId;
        }

        public String version() {
            return this.version;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (GetChangelogFromCoordinates) obj;
            return Objects.equals(this.groupId, that.groupId) &&
                Objects.equals(this.artifactId, that.artifactId) &&
                Objects.equals(this.version, that.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.groupId, this.artifactId, this.version);
        }

        @Override
        public String toString() {
            return "GetChangelogFromCoordinates[" +
                "groupId=" + this.groupId + ", " +
                "artifactId=" + this.artifactId + ", " +
                "version=" + this.version + ']';
        }
    }

    @JsonDeserialize
    final static class GetChangelog
        implements ChangelogCommand, PersistentEntity.ReplyType<ChangelogResponse> {
        private final Artifact artifact;

        public GetChangelog(Artifact artifact) {
            this.artifact = artifact;
        }

        public Artifact artifact() {
            return this.artifact;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (GetChangelog) obj;
            return Objects.equals(this.artifact, that.artifact);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.artifact);
        }

        @Override
        public String toString() {
            return "GetChangelog[" +
                "artifact=" + this.artifact + ']';
        }
    }
}
