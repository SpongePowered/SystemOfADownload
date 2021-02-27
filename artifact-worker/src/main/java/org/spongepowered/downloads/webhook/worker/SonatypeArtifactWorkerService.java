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
import akka.actor.ActorSystem;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.stream.javadsl.Flow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import io.vavr.control.Try;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.changelog.api.ChangelogService;
import org.spongepowered.downloads.utils.AuthUtils;
import org.spongepowered.downloads.webhook.ArtifactWorker;
import org.spongepowered.downloads.webhook.ScrapedArtifactEvent;
import org.spongepowered.downloads.webhook.SonatypeWebhookService;

import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.function.Function;

public class SonatypeArtifactWorkerService implements ArtifactWorker {

    static final Logger LOGGER = LogManager.getLogger("ArtifactWorker");
    static final Marker SERVICE = MarkerManager.getMarker("ArtifactScraper");

    private final Function<Class<? extends ScrapedArtifactEvent>, Optional<WorkerStep<?>>> WORKER_STAGES;

    final ArtifactService artifacts;
    final ChangelogService changelog;
    final SonatypeWebhookService webhookService;
    final PersistentEntityRegistry registry;
    final ClusterSharding clusterSharding;
    private final ObjectMapper objectMapper;

    @Inject
    public SonatypeArtifactWorkerService(
        final ArtifactService artifacts, final ChangelogService changelog,
        final SonatypeWebhookService webhookService,
        final PersistentEntityRegistry registry,
        final ClusterSharding clusterSharding,
        final ActorSystem actorSystem,
        final ObjectMapper objectMapper
    ) {
        this.artifacts = artifacts;
        this.changelog = changelog;
        this.registry = registry;

        webhookService.topic().subscribe()
            .atLeastOnce(Flow.<ScrapedArtifactEvent>create().map(this::processEvent));
        this.webhookService = webhookService;
        this.clusterSharding = clusterSharding;
        this.clusterSharding.init(
            Entity.of(
                ScrapedArtifactEntity.ENTITY_TYPE_KEY,
                ScrapedArtifactEntity::create
            )
        );
        this.objectMapper = objectMapper;

        final IdentityHashMap<Class<? extends ScrapedArtifactEvent>, WorkerStep<?>> steps = new IdentityHashMap<>();
        steps.put(ScrapedArtifactEvent.InitializeArtifactForProcessing.class, new RegisterArtifactsStep(this.objectMapper));
        steps.put(ScrapedArtifactEvent.AssociateCommitSha.class, new FetchPriorBuildStep(this.objectMapper));
        steps.put(ScrapedArtifactEvent.AssociatedMavenMetadata.class, new AssociateMavenMetadataStep(this.objectMapper));
        steps.put(ScrapedArtifactEvent.ArtifactRequested.class, new RequestArtifactStep(this.objectMapper));
        WORKER_STAGES = (eventClass) -> Optional.ofNullable(steps.get(eventClass));
    }

    static <Request, Response> ServiceCall<Request, Response> authorizeInvoke(final ServiceCall<Request, Response> call) {
        return call.handleRequestHeader(requestHeader -> requestHeader.withHeader(AuthUtils.INTERNAL_HEADER_KEY, AuthUtils.INTERNAL_HEADER_SECRET));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Done processEvent(final ScrapedArtifactEvent event) {
        LOGGER.log(Level.INFO, "Receiving Event {}", event);
       return this.WORKER_STAGES.apply(event.getClass())
            .map(step -> {
                LOGGER.log(
                    Level.INFO,
                    step.marker(),
                    "Starting process of event {}[{}]",
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



    static {

    }

    public EntityRef<ScrapedArtifactCommand> getProcessingEntity(final String previousBuildCoordinates) {
        return this.clusterSharding.entityRefFor(ScrapedArtifactEntity.ENTITY_TYPE_KEY, previousBuildCoordinates);
    }
}
