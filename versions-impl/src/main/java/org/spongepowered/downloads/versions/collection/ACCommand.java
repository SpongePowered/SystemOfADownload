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
package org.spongepowered.downloads.versions.collection;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.GetVersionResponse;
import org.spongepowered.downloads.versions.api.models.GetVersionsResponse;
import org.spongepowered.downloads.versions.api.models.TagRegistration;
import org.spongepowered.downloads.versions.api.models.VersionRegistration;
import org.spongepowered.downloads.versions.api.models.tags.ArtifactTagEntry;

import java.io.Serial;
import java.util.Objects;
import java.util.Optional;

public interface ACCommand extends Jsonable {

    final class RegisterArtifact implements ACCommand {
        @Serial private static final long serialVersionUID = -3915075643831556478L;

        public final ArtifactCoordinates coordinates;
        public final ActorRef<NotUsed> replyTo;

        public RegisterArtifact(
            final ArtifactCoordinates coordinates, final ActorRef<NotUsed> replyTo
        ) {
            this.coordinates = coordinates;
            this.replyTo = replyTo;
        }
    }

    record RegisterVersion(MavenCoordinates coordinates,
                           ActorRef<VersionRegistration.Response> replyTo)
        implements ACCommand {

    }

    final record RegisterArtifactTag(ArtifactTagEntry entry, ActorRef<TagRegistration.Response> replyTo) implements ACCommand {}
    final record UpdateArtifactTag(ArtifactTagEntry entry, ActorRef<TagRegistration.Response> replyTo) implements ACCommand {}

    record GetVersions(String groupId, String artifactId, Optional<String> tags,
                       Optional<Integer> limit, Optional<Integer> offset,
                       ActorRef<GetVersionsResponse> replyTo)
        implements ACCommand {
    }

    final record GetSpecificVersion(
        String sanitizedGroupId,
        String sanitizedArtifactId,
        String version,
        ActorRef<GetVersionResponse> replyTo
    ) implements ACCommand {
    }
}
