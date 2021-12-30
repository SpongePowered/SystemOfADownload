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
package org.spongepowered.synchronizer.versionsync;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;


@JsonDeserialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(VersionSyncEvent.RegisteredVersion.class),
    @JsonSubTypes.Type(VersionSyncEvent.RegisteredBatch.class),
    @JsonSubTypes.Type(VersionSyncEvent.StartedBatchRegistration.class),
    @JsonSubTypes.Type(VersionSyncEvent.ResolvedVersion.class),
    @JsonSubTypes.Type(VersionSyncEvent.FailedVersion.class)
})
public sealed interface VersionSyncEvent extends AggregateEvent<VersionSyncEvent>, Jsonable {

    AggregateEventShards<VersionSyncEvent> INSTANCE = AggregateEventTag.sharded(VersionSyncEvent.class, 3);

    @Override
    default AggregateEventTagger<VersionSyncEvent> aggregateTag() {
        return INSTANCE;
    }

    @JsonTypeName("registered-version")
    record RegisteredVersion(MavenCoordinates coordinates) implements VersionSyncEvent {}

    @JsonTypeName("registered-batch")
    record RegisteredBatch(ArtifactCoordinates artifact, List<MavenCoordinates> coordinates) implements VersionSyncEvent {
    }

    @JsonTypeName("started-batch")
    record StartedBatchRegistration(List<MavenCoordinates> batched) implements VersionSyncEvent {
    }

    @JsonTypeName("resolved-version")
    record ResolvedVersion(MavenCoordinates coordinates) implements VersionSyncEvent {
    }

    @JsonTypeName("failed-version")
    record FailedVersion(MavenCoordinates coordinates) implements VersionSyncEvent {
    }
}
