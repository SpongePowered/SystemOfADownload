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
package org.spongepowered.downloads.webhook.worker;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.git.api.CommitSha;
import org.spongepowered.downloads.webhook.sonatype.Component;

import java.util.Objects;

interface ScrapedArtifactCommand {
    final static class AssociateMetadataWithCollection implements ScrapedArtifactCommand {
        public final ArtifactCollection collection;
        public final Component component;
        public final String tagVersion;
        public final ActorRef<NotUsed> replyTo;

        public AssociateMetadataWithCollection(
            final ArtifactCollection collection,
            final Component component,
            final String tagVersion,
            final ActorRef<NotUsed> replyTo
        ) {
            this.collection = collection;
            this.component = component;
            this.tagVersion = tagVersion;
            this.replyTo = replyTo;
        }

        public ArtifactCollection collection() {
            return this.collection;
        }

        public Component component() {
            return this.component;
        }

        public String tagVersion() {
            return this.tagVersion;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final var that = (AssociateMetadataWithCollection) obj;
            return Objects.equals(this.collection, that.collection) &&
                Objects.equals(this.component, that.component) &&
                Objects.equals(this.tagVersion, that.tagVersion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.collection, this.component, this.tagVersion);
        }

        @Override
        public String toString() {
            return "AssociateMetadataWithCollection[" +
                "collection=" + this.collection + ", " +
                "component=" + this.component + ", " +
                "tagVersion=" + this.tagVersion + ']';
        }

    }


    final static class RequestArtifactForProcessing implements ScrapedArtifactCommand {
        public final String groupId;
        public final String artifactId;
        public final String requested;
        public final ActorRef<NotUsed> replyTo;

        public RequestArtifactForProcessing(
            final String groupId, final String artifactId, final String requested,
            final ActorRef<NotUsed> replyTo
        ) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.requested = requested;
            this.replyTo = replyTo;
        }

        public String groupId() {
            return this.groupId;
        }

        public String artifactId() {
            return this.artifactId;
        }

        public String requested() {
            return this.requested;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final var that = (RequestArtifactForProcessing) obj;
            return Objects.equals(this.groupId, that.groupId) &&
                Objects.equals(this.artifactId, that.artifactId) &&
                Objects.equals(this.requested, that.requested);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.groupId, this.artifactId, this.requested);
        }

        @Override
        public String toString() {
            return "RequestArtifactForProcessing[" +
                "groupId=" + this.groupId + ", " +
                "artifactId=" + this.artifactId + ", " +
                "requested=" + this.requested + ']';
        }

    }

    final static class AssociateCommitShaWithArtifact implements ScrapedArtifactCommand {
        public final ArtifactCollection collection;
        public final CommitSha sha;
        public final ActorRef<NotUsed> replyTo;

        public AssociateCommitShaWithArtifact(
            final ArtifactCollection collection,
            final CommitSha sha,
            final ActorRef<NotUsed> replyTo
        ) {
            this.collection = collection;
            this.sha = sha;
            this.replyTo = replyTo;
        }

        public ArtifactCollection collection() {
            return this.collection;
        }

        public CommitSha sha() {
            return this.sha;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final var that = (AssociateCommitShaWithArtifact) obj;
            return Objects.equals(this.collection, that.collection) &&
                Objects.equals(this.sha, that.sha);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.collection, this.sha);
        }

        @Override
        public String toString() {
            return "AssociateCommitShaWithArtifact[" +
                "collection=" + this.collection + ", " +
                "sha=" + this.sha + ']';
        }

    }
}
