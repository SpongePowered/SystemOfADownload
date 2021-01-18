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
import akka.stream.javadsl.Flow;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import io.vavr.control.Try;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.changelog.api.ChangelogService;
import org.spongepowered.downloads.webhook.ScrapedArtifactEvent;
import org.spongepowered.downloads.webhook.SonatypeWebhookService;

import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.function.Function;

public class SonatypeArtifactWorkerService implements Service {

    static final Logger LOGGER = LogManager.getLogger(SonatypeArtifactWorkerService.class);
    static final Marker SERVICE = MarkerManager.getMarker("ArtifactScraper");

    private static final Function<Class<? extends ScrapedArtifactEvent>, Optional<WorkerStep<?>>> WORKER_STAGES;

    final ArtifactService artifacts;
    final ChangelogService changelog;
    final SonatypeWebhookService webhookService;
    final PersistentEntityRegistry registry;

    @Inject
    public SonatypeArtifactWorkerService(
        final ArtifactService artifacts, final ChangelogService changelog,
        final SonatypeWebhookService webhookService,
        final PersistentEntityRegistry registry
    ) {
        this.artifacts = artifacts;
        this.changelog = changelog;
        this.registry = registry;
        webhookService.topic().subscribe()
            .atLeastOnce(Flow.<ScrapedArtifactEvent>create().map(this::processEvent));
        this.webhookService = webhookService;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Done processEvent(final ScrapedArtifactEvent event) {
       return SonatypeArtifactWorkerService.WORKER_STAGES.apply(event.getClass())
            .map(step -> {
                LOGGER.log(
                    Level.DEBUG,
                    step.marker(),
                    "Starting process of event %s[%s]",
                    () -> event.getClass().getSimpleName(),
                    event::toString
                );
                return (Try<Done>) ((WorkerStep) step).processEvent(this, event);
            })
            .orElseGet(() -> {
                LOGGER.log(Level.WARN, SERVICE, String.format("Could not find a worker step for %s event", event.getClass()));
                return Try.success(Done.done());
            })
           .toEither()
           .mapLeft(exception -> {
               LOGGER.error(SERVICE, "Failure in processing event", exception);
               return Done.done();
           })
           .fold(Function.identity(), Function.identity());
    }

    @Override
    public Descriptor descriptor() {
        return Service.named("artifact-worker")
            .withAutoAcl(true)
            ;
    }

    static {
        final IdentityHashMap<Class<? extends ScrapedArtifactEvent>, WorkerStep<?>> steps = new IdentityHashMap<>();
        steps.put(ScrapedArtifactEvent.InitializeArtifactForProcessing.class, new RegisterArtifactsStep());
        steps.put(ScrapedArtifactEvent.AssociateCommitSha.class, new FetchPriorBuildStep());
        steps.put(ScrapedArtifactEvent.AssociatedMavenMetadata.class, new AssociateMavenMetadataStep());
        steps.put(ScrapedArtifactEvent.ArtifactRequested.class, new RequestArtifactStep());
        WORKER_STAGES = (eventClass) -> Optional.ofNullable(steps.get(eventClass));
    }

    public PersistentEntityRef<ScrapedArtifactEntity.Command> getProcessingEntity(final String previousBuildCoordinates) {
        return this.registry.refFor(ScrapedArtifactEntity.class, previousBuildCoordinates);
    }
}
