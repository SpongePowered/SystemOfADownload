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
package org.spongepowered.downloads.artifact.group;

import akka.actor.typed.ActorRef;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GetArtifactDetailsResponse;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;

import java.util.Objects;

public interface GroupCommand extends Jsonable {

    final class GetGroup implements GroupCommand {
        public final String groupId;
        public final ActorRef<GroupResponse> replyTo;

        public GetGroup(final String groupId, final ActorRef<GroupResponse> replyTo) {
            this.groupId = groupId;
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
            final var that = (GetGroup) obj;
            return Objects.equals(this.groupId, that.groupId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.groupId);
        }

        @Override
        public String toString() {
            return "GetGroup[" +
                "groupId=" + this.groupId + ']';
        }

    }

    final class GetArtifacts implements GroupCommand {
        public final String groupId;
        public final ActorRef<GetArtifactsResponse> replyTo;

        public GetArtifacts(final String groupId,  final ActorRef<GetArtifactsResponse> replyTo) {
            this.groupId = groupId;
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
            final var that = (GetArtifacts) obj;
            return Objects.equals(this.groupId, that.groupId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.groupId);
        }

        @Override
        public String toString() {
            return "GetArtifacts[" +
                "groupId=" + this.groupId + ']';
        }

    }

    final class RegisterArtifact implements GroupCommand {
        public final String artifact;
        public final ActorRef<ArtifactRegistration.Response> replyTo;

        @JsonCreator
        public RegisterArtifact(
            final String artifact, final ActorRef<ArtifactRegistration.Response> replyTo
        ) {
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

    final class RegisterGroup implements GroupCommand {
        public final String mavenCoordinates;
        public final String name;
        public final String website;
        public final ActorRef<GroupRegistration.Response> replyTo;

        public RegisterGroup(final String mavenCoordinates, final String name, final String website, final ActorRef<GroupRegistration.Response> replyTo) {
            this.mavenCoordinates = mavenCoordinates;
            this.name = name;
            this.website = website;
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
            final var that = (RegisterGroup) obj;
            return Objects.equals(this.mavenCoordinates, that.mavenCoordinates) &&
                Objects.equals(this.name, that.name) &&
                Objects.equals(this.website, that.website);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.mavenCoordinates, this.name, this.website);
        }

        @Override
        public String toString() {
            return "RegisterGroup[" +
                "mavenCoordinates=" + this.mavenCoordinates + ", " +
                "name=" + this.name + ", " +
                "website=" + this.website + ']';
        }

    }

    public class GetArtifactDetails implements GroupCommand {
        private final String artifactId;
        private final ActorRef<GetArtifactDetailsResponse> replyTo;

        public GetArtifactDetails(
            String artifactId, ActorRef<GetArtifactDetailsResponse> replyTo
        ) {

            this.artifactId = artifactId;
            this.replyTo = replyTo;
        }
    }
}
