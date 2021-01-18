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
package org.spongepowered.downloads.webhook.sonatype;

import io.vavr.collection.List;

import java.util.Objects;
import java.util.Optional;

public final class MavenMetadata {
    private final String groupId;
    private final Versioning versioning;

    public MavenMetadata(final String groupId, final Versioning versioning) {
        this.groupId = groupId;
        this.versioning = versioning;
    }

    public String groupId() {
        return this.groupId;
    }

    public Versioning versioning() {
        return this.versioning;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        final var that = (MavenMetadata) obj;
        return Objects.equals(this.groupId, that.groupId) &&
            Objects.equals(this.versioning, that.versioning);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.groupId, this.versioning);
    }

    @Override
    public String toString() {
        return "MavenMetadata[" +
            "groupId=" + this.groupId + ", " +
            "versioning=" + this.versioning + ']';
    }


    static final class Versioning {
        private final Snapshot snapshot;
        private final Optional<String> latest;
        private final Optional<String> release;
        private final String lastUpdated;
        private final List<String> versions;
        private final VersionMap snapshotVersions;

        Versioning(
            final Snapshot snapshot, final Optional<String> latest, final Optional<String> release, final String lastUpdated,
            final List<String> versions, final VersionMap snapshotVersions
        ) {
            this.snapshot = snapshot;
            this.latest = latest;
            this.release = release;
            this.lastUpdated = lastUpdated;
            this.versions = versions;
            this.snapshotVersions = snapshotVersions;
        }

        public Snapshot snapshot() {
            return this.snapshot;
        }

        public Optional<String> latest() {
            return this.latest;
        }

        public Optional<String> release() {
            return this.release;
        }

        public String lastUpdated() {
            return this.lastUpdated;
        }

        public List<String> versions() {
            return this.versions;
        }

        public VersionMap snapshotVersions() {
            return this.snapshotVersions;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (Versioning) obj;
            return Objects.equals(this.snapshot, that.snapshot) &&
                Objects.equals(this.latest, that.latest) &&
                Objects.equals(this.release, that.release) &&
                Objects.equals(this.lastUpdated, that.lastUpdated) &&
                Objects.equals(this.versions, that.versions) &&
                Objects.equals(this.snapshotVersions, that.snapshotVersions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                this.snapshot, this.latest, this.release, this.lastUpdated, this.versions, this.snapshotVersions);
        }

        @Override
        public String toString() {
            return "Versioning[" +
                "snapshot=" + this.snapshot + ", " +
                "latest=" + this.latest + ", " +
                "release=" + this.release + ", " +
                "lastUpdated=" + this.lastUpdated + ", " +
                "versions=" + this.versions + ", " +
                "snapshotVersions=" + this.snapshotVersions + ']';
        }
    }

    static final class Snapshot {
        private final String timestamp;
        private final Integer buildNumber;

        Snapshot(final String timestamp, final Integer buildNumber) {
            this.timestamp = timestamp;
            this.buildNumber = buildNumber;
        }

        public String timestamp() {
            return this.timestamp;
        }

        public Integer buildNumber() {
            return this.buildNumber;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (Snapshot) obj;
            return Objects.equals(this.timestamp, that.timestamp) &&
                Objects.equals(this.buildNumber, that.buildNumber);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.timestamp, this.buildNumber);
        }

        @Override
        public String toString() {
            return "Snapshot[" +
                "timestamp=" + this.timestamp + ", " +
                "buildNumber=" + this.buildNumber + ']';
        }
    }

    static final class VersionMap {
        private final List<VersionedAsset> assets;

        VersionMap(final List<VersionedAsset> assets) {
            this.assets = assets;
        }

        public List<VersionedAsset> assets() {
            return this.assets;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (VersionMap) obj;
            return Objects.equals(this.assets, that.assets);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.assets);
        }

        @Override
        public String toString() {
            return "VersionMap[" +
                "assets=" + this.assets + ']';
        }
    }

    static final class VersionedAsset {
        private final String classifier;
        private final String extension;
        private final String value;
        private final String updated;

        VersionedAsset(final String classifier, final String extension, final String value, final String updated) {
            this.classifier = classifier;
            this.extension = extension;
            this.value = value;
            this.updated = updated;
        }

        public String classifier() {
            return this.classifier;
        }

        public String extension() {
            return this.extension;
        }

        public String value() {
            return this.value;
        }

        public String updated() {
            return this.updated;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (VersionedAsset) obj;
            return Objects.equals(this.classifier, that.classifier) &&
                Objects.equals(this.extension, that.extension) &&
                Objects.equals(this.value, that.value) &&
                Objects.equals(this.updated, that.updated);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.classifier, this.extension, this.value, this.updated);
        }

        @Override
        public String toString() {
            return "VersionedAsset[" +
                "classifier=" + this.classifier + ", " +
                "extension=" + this.extension + ", " +
                "value=" + this.value + ", " +
                "updated=" + this.updated + ']';
        }
    }
}
