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
package org.spongepowered.downloads.versions.sonatype.resync;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.maven.artifact.ArtifactMavenMetadata;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

interface SynchronizeEvent extends Jsonable, AggregateEvent<SynchronizeEvent> {

    AggregateEventShards<SynchronizeEvent> TAG = AggregateEventTag.sharded(SynchronizeEvent.class, 10);

    @Override
    default AggregateEventTagger<SynchronizeEvent> aggregateTag() {
        return TAG;
    }

    @JsonDeserialize
    final class SynchronizedArtifacts implements SynchronizeEvent {

        public final ArtifactMavenMetadata metadata;
        public final String updatedTime;

        @JsonCreator
        public SynchronizedArtifacts(final ArtifactMavenMetadata metadata, final String updatedTime) {
            this.metadata = Objects.requireNonNull(metadata, "metadata");
            this.updatedTime = Objects.requireNonNull(updatedTime, "updatedTime");
        }
    }
}
