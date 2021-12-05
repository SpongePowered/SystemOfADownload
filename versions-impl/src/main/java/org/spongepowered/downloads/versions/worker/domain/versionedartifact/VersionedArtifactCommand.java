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
package org.spongepowered.downloads.versions.worker.domain.versionedartifact;

import akka.Done;
import akka.actor.typed.ActorRef;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.VersionedCommit;

import java.net.URI;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = VersionedArtifactCommand.Register.class, name = "register"),
    @JsonSubTypes.Type(value = VersionedArtifactCommand.AddAssets.class, name = "add-assets"),
    @JsonSubTypes.Type(value = VersionedArtifactCommand.MarkFilesAsErrored.class, name = "errored-assets"),
    @JsonSubTypes.Type(value = VersionedArtifactCommand.RegisterRawCommit.class, name = "register-sha"),
    @JsonSubTypes.Type(value = VersionedArtifactCommand.RegisterResolvedCommit.class, name = "register-commit"),
})
@JsonDeserialize
public sealed interface VersionedArtifactCommand extends Jsonable {

    final record Register(
        MavenCoordinates coordinates,
        ActorRef<Done> replyTo
    ) implements VersionedArtifactCommand {
        @JsonCreator
        public Register {
        }
    }

    final record AddAssets(
        MavenCoordinates coordinates,
        List<Artifact> artifacts,
        ActorRef<Done> replyTo
    ) implements VersionedArtifactCommand {
        @JsonCreator
        public AddAssets {
        }
    }

    final record MarkFilesAsErrored() implements VersionedArtifactCommand {
        @JsonCreator
        public MarkFilesAsErrored {
        }
    }

    final record RegisterRawCommit(String commitSha) implements VersionedArtifactCommand {}

    final record RegisterResolvedCommit(
        VersionedCommit versionedCommit,
        URI repo,
        ActorRef<Done> replyTo
    )
        implements VersionedArtifactCommand {
        @JsonCreator
        public RegisterResolvedCommit {
        }
    }
}
