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
package org.spongepowered.synchronizer.resync.domain;

import akka.actor.typed.ActorRef;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.maven.artifact.ArtifactMavenMetadata;

@JsonDeserialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Command.Resync.class, name = "resync"),
    @JsonSubTypes.Type(value = Command.Failed.class, name = "failed"),
    @JsonSubTypes.Type(value = Command.WrappedResync.class, name = "wrapped-resync"),
    @JsonSubTypes.Type(value = Command.Completed.class, name = "completed"),
})
public interface Command extends Jsonable {
    @JsonDeserialize
    record Resync(
        ArtifactCoordinates coordinates,
        ActorRef<List<MavenCoordinates>> replyTo
    ) implements Command {
        @JsonCreator
        public Resync {}

    }

    record Completed(ArtifactMavenMetadata metadata) implements Response {
        @JsonCreator
        public Completed {
        }
    }

    @JsonDeserialize
    sealed interface Response extends Command {
    }

    @JsonDeserialize
    record Failed() implements Response {
        @JsonCreator
        public Failed {
        }
    }

    record WrappedResync(
        Response response,
        ActorRef<List<MavenCoordinates>> replyTo
    ) implements Response {
        @JsonCreator
        public WrappedResync {
        }
    }
}
