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
package org.spongepowered.downloads.artifact.api.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(ArtifactUpdate.ArtifactRegistered.class),
    @JsonSubTypes.Type(ArtifactUpdate.GitRepositoryAssociated.class),
    @JsonSubTypes.Type(ArtifactUpdate.WebsiteUpdated.class),
    @JsonSubTypes.Type(ArtifactUpdate.IssuesUpdated.class),
    @JsonSubTypes.Type(ArtifactUpdate.DisplayNameUpdated.class),
})
public interface ArtifactUpdate extends Jsonable {

    ArtifactCoordinates coordinates();

    default String partitionKey() {
        return this.coordinates().asMavenString();
    }

    @JsonTypeName("registered")
    @JsonDeserialize
    final record ArtifactRegistered(
        ArtifactCoordinates coordinates
    ) implements ArtifactUpdate {

        @JsonCreator
        public ArtifactRegistered {
        }
    }

    @JsonTypeName("git-repository")
    @JsonDeserialize
    final record GitRepositoryAssociated(
        ArtifactCoordinates coordinates,
        String repository
    ) implements ArtifactUpdate {

        @JsonCreator
        public GitRepositoryAssociated {
        }
    }

    @JsonTypeName("website")
    @JsonDeserialize
    final record WebsiteUpdated(
        ArtifactCoordinates coordinates,
        String url
    ) implements ArtifactUpdate {

        @JsonCreator
        public WebsiteUpdated {
        }
    }

    @JsonTypeName("issues")
    @JsonDeserialize
    final record IssuesUpdated(
        ArtifactCoordinates coordinates,
        String url
    ) implements ArtifactUpdate {

        @JsonCreator
        public IssuesUpdated {
        }
    }

    @JsonTypeName("displayName")
    @JsonDeserialize
    final record DisplayNameUpdated(
        ArtifactCoordinates coordinates,
        String displayName
    ) implements ArtifactUpdate {

        @JsonCreator
        public DisplayNameUpdated {
        }
    }

}
