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
package org.spongepowered.downloads.changelog;

import akka.Done;
import akka.japi.Pair;
import akka.stream.Attributes;
import akka.stream.javadsl.Flow;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.Offset;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import org.pcollections.PSequence;
import org.spongepowered.downloads.artifact.event.ArtifactEvent;
import org.spongepowered.downloads.changelog.api.ChangelogService;

import java.util.concurrent.CompletableFuture;

public class ArtifactReadSideProcessor extends ReadSideProcessor<ArtifactEvent> {

    final ChangelogService changelogService;

    @Inject
    public ArtifactReadSideProcessor(final ChangelogService service) {
        this.changelogService = service;
    }

    @Override
    public ReadSideHandler<ArtifactEvent> buildHandler() {
        return new ReadSideHandler<>() {
            @Override
            public Flow<Pair<ArtifactEvent, Offset>, Done, ?> handle() {
                return Flow.<Pair<ArtifactEvent, Offset>>create()
                    .log("ArtifactEvent")
                    .withAttributes(Attributes.createLogLevels(Attributes.logLevelInfo()))
                    .mapAsyncUnordered(10, e -> {
                        final ArtifactEvent event = e.first();
                        if (event instanceof ArtifactEvent.ArtifactRegistered) {
                            final var registered = (ArtifactEvent.ArtifactRegistered) event;
                            return ArtifactReadSideProcessor.this.changelogService
                                .registerArtifact(registered.artifact()).invoke()
                                .thenApply(none -> Done.done());
                        }
                        return CompletableFuture.completedFuture(Done.done());
                    });
            }
        };
    }

    @Override
    public PSequence<AggregateEventTag<ArtifactEvent>> aggregateTags() {
        return ArtifactEvent.INSTANCE.allTags();
    }
}
