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
package org.spongepowered.downloads.artifact.details;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.api.transport.NotFound;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.control.Either;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.query.ArtifactDetails;

import java.net.URL;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
    property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = DetailsCommand.RegisterArtifact.class,
        name = "register"),
    @JsonSubTypes.Type(value = DetailsCommand.UpdateWebsite.class,
        name = "website"),
    @JsonSubTypes.Type(value = DetailsCommand.UpdateGitRepository.class,
        name = "git-repository"
    ),
    @JsonSubTypes.Type(value = DetailsCommand.UpdateIssues.class,
        name = "issues"),
    @JsonSubTypes.Type(value = DetailsCommand.UpdateDisplayName.class,
        name = "display-name")
})
public interface DetailsCommand extends Jsonable {

    @JsonDeserialize
    final record RegisterArtifact(ArtifactCoordinates coordinates,
                                  String displayName, ActorRef<NotUsed> replyTo)
        implements DetailsCommand {

        @JsonCreator
        public RegisterArtifact {
        }
    }

    @JsonDeserialize
    final record UpdateWebsite(
        ArtifactCoordinates coordinates,
        URL website,
        ActorRef<Either<NotFound, ArtifactDetails.Response>> replyTo
    ) implements DetailsCommand {

        @JsonCreator
        public UpdateWebsite {
        }
    }

    @JsonDeserialize
    final record UpdateDisplayName(
        ArtifactCoordinates coordinates,
        String displayName,
        ActorRef<Either<NotFound, ArtifactDetails.Response>> replyTo
    ) implements DetailsCommand {

        @JsonCreator
        public UpdateDisplayName {
        }
    }

    @JsonDeserialize
    final record UpdateGitRepository(
        ArtifactCoordinates coordinates,
        String gitRemote,
        ActorRef<Either<NotFound, ArtifactDetails.Response>> replyTo
    ) implements DetailsCommand {

        @JsonCreator
        public UpdateGitRepository {
        }
    }

    @JsonDeserialize
    final record UpdateIssues(
        ArtifactCoordinates coords,
        URL validUrl,
        ActorRef<Either<NotFound, ArtifactDetails.Response>> replyTo
    ) implements DetailsCommand {

        @JsonCreator
        public UpdateIssues {
        }
    }
}
