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
package org.spongepowered.downloads.versions.api.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;

public final class VersionRegistration {


    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Register.Collection.class,
            name = "Collection"),
        @JsonSubTypes.Type(value = Register.Version.class,
            name = "Version"),
    })
    public interface Register {

        @JsonDeserialize
        final record Collection(
            @JsonProperty ArtifactCollection collection
        ) implements Register {

            @JsonCreator
            public Collection {
            }

        }

        @JsonDeserialize
        record Version(@JsonProperty MavenCoordinates coordinates)
            implements Register {
            @JsonCreator
            public Version {
            }

        }


    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Response.GroupMissing.class,
            name = "GroupMissing"),
        @JsonSubTypes.Type(value = Response.ArtifactAlreadyRegistered.class,
            name = "AlreadyRegistered"),
        @JsonSubTypes.Type(value = Response.RegisteredArtifact.class,
            name = "Registered"),
    })
    public interface Response extends Jsonable {

        @JsonDeserialize
        final record ArtifactAlreadyRegistered(
            @JsonProperty(required = true) MavenCoordinates coordinates
        ) implements Response {

            @JsonCreator
            public ArtifactAlreadyRegistered {
            }
        }

        @JsonDeserialize
        final record RegisteredArtifact(
            @JsonProperty(required = true) MavenCoordinates mavenCoordinates
        ) implements Response {

            @JsonCreator
            public RegisteredArtifact {
            }
        }

        @JsonDeserialize
        final record GroupMissing(@JsonProperty(required = true) String groupId) implements Response {

        }
    }
}
