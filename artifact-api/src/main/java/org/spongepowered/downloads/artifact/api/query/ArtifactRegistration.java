package org.spongepowered.downloads.artifact.api.query;

import com.lightbend.lagom.serialization.Jsonable;
import io.swagger.v3.oas.annotations.media.Schema;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;

import java.io.Serial;
import java.util.Objects;

public final class ArtifactRegistration {

    public static final class RegisterCollection {
        @Schema(required = true) private final ArtifactCollection collection;

        public RegisterCollection(@Schema(required = true) ArtifactCollection collection) {
            this.collection = collection;
        }

        @Schema(required = true)
        public ArtifactCollection collection() {
            return this.collection;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (RegisterCollection) obj;
            return Objects.equals(this.collection, that.collection);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.collection);
        }

        @Override
        public String toString() {
            return "RegisterCollection[" +
                "collection=" + this.collection + ']';
        }
    }

    public interface Response extends Jsonable {

        final static class ArtifactAlreadyRegistered implements
            Response {
            @Serial private static final long serialVersionUID = 0L;
            private final String artifactName;
            private final String groupId;

            public ArtifactAlreadyRegistered(String artifactName, String groupId) {
                this.artifactName = artifactName;
                this.groupId = groupId;
            }

            public String artifactName() {
                return this.artifactName;
            }

            public String groupId() {
                return this.groupId;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                var that = (ArtifactAlreadyRegistered) obj;
                return Objects.equals(this.artifactName, that.artifactName) &&
                    Objects.equals(this.groupId, that.groupId);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.artifactName, this.groupId);
            }

            @Override
            public String toString() {
                return "ArtifactAlreadyRegistered[" +
                    "artifactName=" + this.artifactName + ", " +
                    "groupId=" + this.groupId + ']';
            }
        }

        final static class RegisteredArtifact implements Response {
            @Serial private static final long serialVersionUID = 0L;
            private final ArtifactCollection artifact;

            public RegisteredArtifact(ArtifactCollection artifact) {
                this.artifact = artifact;
            }

            public String getMavenCoordinates() {
                return this.artifact.getMavenCoordinates();
            }

            public ArtifactCollection artifact() {
                return this.artifact;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                var that = (RegisteredArtifact) obj;
                return Objects.equals(this.artifact, that.artifact);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.artifact);
            }

            @Override
            public String toString() {
                return "RegisteredArtifact[" +
                    "artifact=" + this.artifact + ']';
            }

        }

        final static class GroupMissing implements Response {
            @Serial private static final long serialVersionUID = 0L;
            private final String s;

            public GroupMissing(String s) {
                this.s = s;
            }

            public String s() {
                return this.s;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                var that = (GroupMissing) obj;
                return Objects.equals(this.s, that.s);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.s);
            }

            @Override
            public String toString() {
                return "GroupMissing[" +
                    "s=" + this.s + ']';
            }
        }

    }
}
