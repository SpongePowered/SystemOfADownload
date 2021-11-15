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
package org.spongepowered.downloads.versions.server.domain;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.TagRegistration;
import org.spongepowered.downloads.versions.api.models.TagVersion;
import org.spongepowered.downloads.versions.api.models.VersionRegistration;
import org.spongepowered.downloads.versions.api.models.tags.ArtifactTagEntry;

@JsonDeserialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ACCommand.RegisterArtifact.class, name = "register-artifact"),
    @JsonSubTypes.Type(value = ACCommand.RegisterVersion.class, name = "register-version"),
    @JsonSubTypes.Type(value = ACCommand.RegisterArtifactTag.class, name = "register-tag"),
    @JsonSubTypes.Type(value = ACCommand.UpdateArtifactTag.class, name = "update-tag"),
    @JsonSubTypes.Type(value = ACCommand.RegisterPromotion.class, name = "register-promotion"),
    @JsonSubTypes.Type(value = ACCommand.RegisterCollection.class, name = "register-collection"),
    @JsonSubTypes.Type(value = ACCommand.GetCollections.class, name = "get-collection"),
})
public sealed interface ACCommand extends Jsonable{

    final record RegisterArtifact(
        ArtifactCoordinates coordinates,
        ActorRef<NotUsed> replyTo
    ) implements ACCommand {
    }

    final record RegisterVersion(
        MavenCoordinates coordinates,
        ActorRef<VersionRegistration.Response> replyTo
    ) implements ACCommand {
    }

    final record RegisterArtifactTag(
        ArtifactTagEntry entry,
        ActorRef<TagRegistration.Response> replyTo
    ) implements ACCommand {
    }

    final record UpdateArtifactTag(
        ArtifactTagEntry entry,
        ActorRef<TagRegistration.Response> replyTo
    ) implements ACCommand {
    }

    final record RegisterPromotion(
        String regex,
        ActorRef<TagVersion.Response> replyTo,
        boolean enableManualMarking
    ) implements ACCommand {
    }

    final record RegisterCollection(
        ArtifactCollection collection,
        ActorRef<VersionRegistration.Response> replyTo
    ) implements ACCommand {
    }

    final record GetVersions(
        ActorRef<List<MavenCoordinates>> replyTo
    ) implements ACCommand {
    }

    final record GetCollections(
        List<MavenCoordinates> coordinates,
        ActorRef<List<ArtifactCollection>> replyTo
    ) implements ACCommand {}
}
