package org.spongepowered.downloads.artifact.api.query;

import io.vavr.collection.Map;
import io.vavr.collection.TreeMap;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;

import java.util.Objects;

public final class GetTaggedArtifacts {

    public interface Request {
        Type getType();
        String getTagType();
    }

    public enum Type { VERSION, SNAPSHOT; }

    public final static class MavenVersion implements Request {
        private final String versionPart;
        private final boolean includePrevious;

        public MavenVersion(final String versionPart, final boolean includePrevious) {
            this.versionPart = versionPart;
            this.includePrevious = includePrevious;
        }

        @Override
        public String getTagType() {
            return Type.VERSION.name();
        }

        @Override
        public Type getType() {
            return Type.VERSION;
        }

        public String versionPart() {
            return this.versionPart;
        }

        public boolean includePrevious() {
            return this.includePrevious;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (MavenVersion) obj;
            return Objects.equals(this.versionPart, that.versionPart) &&
                this.includePrevious == that.includePrevious;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.versionPart, this.includePrevious);
        }

        @Override
        public String toString() {
            return "MavenVersion[" +
                "versionPart=" + this.versionPart + ", " +
                "includePrevious=" + this.includePrevious + ']';
        }

    }

    public final static class SnapshotBuilds implements Request {
        private final String mavenVersion;
        private final boolean includePrevious;

        public SnapshotBuilds(final String mavenVersion, final boolean includePrevious) {
            this.mavenVersion = mavenVersion;
            this.includePrevious = includePrevious;
        }

        @Override
        public String getTagType() {
            return Type.SNAPSHOT.name();
        }

        @Override
        public Type getType() {
            return Type.SNAPSHOT;
        }

        public String mavenVersion() {
            return this.mavenVersion;
        }

        public boolean includePrevious() {
            return this.includePrevious;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (SnapshotBuilds) obj;
            return Objects.equals(this.mavenVersion, that.mavenVersion) &&
                this.includePrevious == that.includePrevious;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.mavenVersion, this.includePrevious);
        }

        @Override
        public String toString() {
            return "SnapshotBuilds[" +
                "mavenVersion=" + this.mavenVersion + ", " +
                "includePrevious=" + this.includePrevious + ']';
        }

    }

    public interface Response {

        final static class TagUnknown implements Response {
            private final String tag;

            public TagUnknown(final String tag) {
                this.tag = tag;
            }

            public String tag() {
                return this.tag;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (TagUnknown) obj;
                return Objects.equals(this.tag, that.tag);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.tag);
            }

            @Override
            public String toString() {
                return "TagUnknown[" +
                    "tag=" + this.tag + ']';
            }

        }
    }

    public final static class VersionsAvailable implements Response {
        private final TreeMap<String, String> artifacts;

        public VersionsAvailable(final TreeMap<String, String> artifacts) {
            this.artifacts = artifacts;
        }

        public TreeMap<String, String> artifacts() {
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

    public final static class GroupUnknown implements Response {
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

    public final static class ArtifactUnknown implements Response {
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
