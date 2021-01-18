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
package org.spongepowered.downloads.artifact.api.query;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.vavr.collection.TreeMap;

import java.util.Objects;

public final class GetTaggedArtifacts {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Request.MavenVersion.class, name = "Version"),
        @JsonSubTypes.Type(value = Request.SnapshotBuilds.class, name = "Snapshot")
    })
    public interface Request {
        Type getType();
        String getTagType();

        /**
         * Gets whether this request includes previous builds, usually a flag
         * to queue requests.
         *
         * @return Whether to queue previous versions to be downloaded
         */
        boolean includePrevious();

        @JsonSerialize
        final class SnapshotBuilds implements Request {
            @JsonProperty(required = true)
            private final String mavenVersion;
            @JsonProperty
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

        final class MavenVersion implements Request {
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
    }

    public enum Type { VERSION, SNAPSHOT; }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Response.TagUnknown.class, name = "UnknownTag"),
        @JsonSubTypes.Type(value = Response.VersionsAvailable.class, name = "Artifacts"),
        @JsonSubTypes.Type(value = Response.ArtifactUnknown.class, name = "UnknownArtifact"),
        @JsonSubTypes.Type(value = Response.GroupUnknown.class, name = "UnknownGroup")
    })
    public interface Response {

        final class TagUnknown implements Response {
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

        final class VersionsAvailable implements Response {
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

        final class ArtifactUnknown implements Response {
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

        final class GroupUnknown implements Response {
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
    }

}
