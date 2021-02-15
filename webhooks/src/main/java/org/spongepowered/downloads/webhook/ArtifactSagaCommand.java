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
package org.spongepowered.downloads.webhook;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.webhook.sonatype.Component;

import java.util.Objects;

public interface ArtifactSagaCommand {

    final class StartProcessing implements ArtifactSagaCommand {
        public final SonatypeData webhook;
        public final MavenCoordinates artifact;
        public final ActorRef<NotUsed> replyTo;

        public StartProcessing(
            final SonatypeData webhook,
            final MavenCoordinates artifact,
            final ActorRef<NotUsed> replyTo
        ) {
            this.webhook = webhook;
            this.artifact = artifact;
            this.replyTo = replyTo;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final var that = (StartProcessing) obj;
            return Objects.equals(this.webhook, that.webhook) &&
                Objects.equals(this.artifact, that.artifact);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.webhook, this.artifact);
        }

        @Override
        public String toString() {
            return "StartProcessing[" +
                "webhook=" + this.webhook + ", " +
                "artifact=" + this.artifact + ']';
        }

    }

    final class AssociateMetadataWithCollection implements ArtifactSagaCommand {
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


}
