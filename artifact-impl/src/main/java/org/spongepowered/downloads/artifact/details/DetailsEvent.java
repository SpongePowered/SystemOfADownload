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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

public interface DetailsEvent extends AggregateEvent<DetailsEvent>, Jsonable {

    AggregateEventShards<DetailsEvent> TAG = AggregateEventTag.sharded(DetailsEvent.class, 10);

    @Override
    default AggregateEventTagger<DetailsEvent> aggregateTag() {
        return TAG;
    }

    @JsonDeserialize
    final class ArtifactRegistered implements DetailsEvent {
        public final ArtifactCoordinates coordinates;

        @JsonCreator
        public ArtifactRegistered(final ArtifactCoordinates coordinates) {
            this.coordinates = coordinates;
        }
    }

    @JsonDeserialize
    final class ArtifactDetailsUpdated implements DetailsEvent {
        public final ArtifactCoordinates coordinates;
        public final String displayName;

        @JsonCreator
        public ArtifactDetailsUpdated(final ArtifactCoordinates coordinates, final String displayName) {
            this.coordinates = coordinates;
            this.displayName = displayName;
        }
    }
}
