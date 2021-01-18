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
package org.spongepowered.downloads.changelog.api.query;

import org.spongepowered.downloads.changelog.api.Changelog;

import java.util.Objects;

public interface ChangelogResponse {

    final static class ArtifactMissing implements ChangelogResponse {
        private final String artifactId;
        private final String groupId;
        private final String version;

        public ArtifactMissing(final String artifactId, final String groupId, final String version) {
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
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (ArtifactMissing) obj;
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

        public GroupMissing(final String groupId) {
            this.groupId = groupId;
        }

        public String groupId() {
            return this.groupId;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (GroupMissing) obj;
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

        public VersionMissing(final String version, final String artifactId, final String groupId) {
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
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (VersionMissing) obj;
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

        public VersionedChangelog(final Changelog changeLog) {
            this.changeLog = changeLog;
        }

        public Changelog changeLog() {
            return this.changeLog;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (VersionedChangelog) obj;
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
