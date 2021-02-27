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
package org.spongepowered.downloads.webhook.worker;

import akka.Done;
import akka.NotUsed;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vavr.control.Try;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.webhook.ScrapedArtifactEvent;
import org.spongepowered.downloads.webhook.sonatype.SonatypeClient;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class AssociateMavenMetadataStep implements WorkerStep<ScrapedArtifactEvent.AssociatedMavenMetadata> {

    private static final Logger LOGGER = LogManager.getLogger("SonatypeArtifactMetadataFetcher");
    private static final Marker MARKER = MarkerManager.getMarker("AssociateMetadata");
    private final ObjectMapper objectMapper;

    public AssociateMavenMetadataStep(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Try<Done> processEvent(
        final SonatypeArtifactWorkerService service,
        final ScrapedArtifactEvent.AssociatedMavenMetadata event
    ) {
        final SonatypeClient client = SonatypeClient.configureClient(this.objectMapper).apply();
        final Artifact base = event.collection().getArtifactComponents().get("base")
            .getOrElse(() -> event.collection().getArtifactComponents().head()._2);
        return Try.of(() -> client.generateArtifactFrom(base)
            .map(sha -> service.getProcessingEntity(event.mavenCoordinates())
                .<NotUsed>ask(replyTo -> new ScrapedArtifactCommand.AssociateCommitShaWithArtifact(event.collection(), sha, replyTo),
                    Duration.ofSeconds(10))
            )
            .map(notUsed -> notUsed.thenApply(notUsed1 -> Done.done()))
            .toEither()
            .mapLeft(throwable -> {
                SonatypeArtifactWorkerService.LOGGER.log(Level.WARN, MARKER, throwable.getMessage());
                return CompletableFuture.completedFuture(Done.done());
            })
            .fold(Function.identity(), Function.identity())
            .toCompletableFuture()
            .join()
        );
    }

    @Override
    public Marker marker() {
        return MARKER;
    }

    @Override
    public Logger logger() {
        return LOGGER;
    }

    @Override
    public boolean equals(final Object obj) {
        return obj == this || obj != null && obj.getClass() == this.getClass();
    }

    @Override
    public int hashCode() {
        return 1;
    }

    @Override
    public String toString() {
        return "AssociateMavenMetadataStep[]";
    }

}
