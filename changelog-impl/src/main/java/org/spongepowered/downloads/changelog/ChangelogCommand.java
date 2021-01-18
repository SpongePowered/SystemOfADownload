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

        public RegisterArtifact(final Artifact artifact) {
            this.artifact = artifact;
        }

        public Artifact artifact() {
            return this.artifact;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (RegisterArtifact) obj;
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

        public GetChangelogFromCoordinates(final String groupId, final String artifactId, final String version) {
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
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (GetChangelogFromCoordinates) obj;
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

        public GetChangelog(final Artifact artifact) {
            this.artifact = artifact;
        }

        public Artifact artifact() {
            return this.artifact;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (GetChangelog) obj;
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
