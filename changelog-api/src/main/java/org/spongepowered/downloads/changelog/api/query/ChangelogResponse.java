package org.spongepowered.downloads.changelog.api.query;

import org.spongepowered.downloads.changelog.api.Changelog;

import java.util.Objects;

public interface ChangelogResponse {

    final static class ArtifactMissing implements ChangelogResponse {
        private final String artifactId;
        private final String groupId;
        private final String version;

        public ArtifactMissing(String artifactId, String groupId, String version) {
            this.artifactId = artifactId;
            this.groupId = groupId;
            this.version = version;
        }

        public String artifactId() {
            return this.artifactId;
        }

        public String groupId() {
            return this.groupId;
        }

        public String version() {
            return this.version;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (ArtifactMissing) obj;
            return Objects.equals(this.artifactId, that.artifactId) &&
                Objects.equals(this.groupId, that.groupId) &&
                Objects.equals(this.version, that.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.artifactId, this.groupId, this.version);
        }

        @Override
        public String toString() {
            return "ArtifactMissing[" +
                "artifactId=" + this.artifactId + ", " +
                "groupId=" + this.groupId + ", " +
                "version=" + this.version + ']';
        }
    }

    final static class GroupMissing implements ChangelogResponse {
        private final String groupId;

        public GroupMissing(String groupId) {
            this.groupId = groupId;
        }

        public String groupId() {
            return this.groupId;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (GroupMissing) obj;
            return Objects.equals(this.groupId, that.groupId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.groupId);
        }

        @Override
        public String toString() {
            return "GroupMissing[" +
                "groupId=" + this.groupId + ']';
        }
    }

    final static class VersionMissing implements ChangelogResponse {
        private final String version;
        private final String artifactId;
        private final String groupId;

        public VersionMissing(String version, String artifactId, String groupId) {
            this.version = version;
            this.artifactId = artifactId;
            this.groupId = groupId;
        }

        public String version() {
            return this.version;
        }

        public String artifactId() {
            return this.artifactId;
        }

        public String groupId() {
            return this.groupId;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (VersionMissing) obj;
            return Objects.equals(this.version, that.version) &&
                Objects.equals(this.artifactId, that.artifactId) &&
                Objects.equals(this.groupId, that.groupId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.version, this.artifactId, this.groupId);
        }

        @Override
        public String toString() {
            return "VersionMissing[" +
                "version=" + this.version + ", " +
                "artifactId=" + this.artifactId + ", " +
                "groupId=" + this.groupId + ']';
        }
    }

    final static class VersionedChangelog implements ChangelogResponse {
        private final Changelog changeLog;

        public VersionedChangelog(Changelog changeLog) {
            this.changeLog = changeLog;
        }

        public Changelog changeLog() {
            return this.changeLog;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (VersionedChangelog) obj;
            return Objects.equals(this.changeLog, that.changeLog);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.changeLog);
        }

        @Override
        public String toString() {
            return "VersionedChangelog[" +
                "changeLog=" + this.changeLog + ']';
        }
    }
}
