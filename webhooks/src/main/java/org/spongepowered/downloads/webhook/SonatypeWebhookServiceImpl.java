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
package org.spongepowered.downloads.webhook;


import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.japi.Pair;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol;
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.server.HeaderServiceCall;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.pac4j.core.config.Config;
import org.pac4j.lagom.javadsl.SecuredService;
import org.pcollections.HashTreePMap;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.auth.AuthenticatedInternalService;
import org.spongepowered.downloads.auth.api.SOADAuth;
import org.spongepowered.downloads.changelog.api.ChangelogService;
import org.spongepowered.downloads.utils.AuthUtils;
import org.taymyr.lagom.javadsl.openapi.AbstractOpenAPIService;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class SonatypeWebhookServiceImpl extends AbstractOpenAPIService implements SonatypeWebhookService,
    AuthenticatedInternalService {

    private static final Logger LOGGER = LogManager.getLogger("SonatypeWebhook");
    public static final Marker HEADER_AUTH = MarkerManager.getMarker("AUTH");
    public static final String TOPIC_NAME = "artifact-changelog-analysis";
    private final ArtifactService artifacts;
    private final ChangelogService changelog;
    private final ClusterSharding clusterSharding;
    private final PersistentEntityRegistry persistentEntityRegistry;
    private final Config securityConfig;
    public static final String NEXUS_SECRET_HEADER = "X-Nexus-Webhook-Signature";

    @Inject
    public SonatypeWebhookServiceImpl(
        final ArtifactService artifacts, final ChangelogService changelog,
        final ClusterSharding clusterSharding,
        final PersistentEntityRegistry persistentEntityRegistry,
        @SOADAuth final Config securityConfig
    ) {
        this.artifacts = artifacts;
        this.changelog = changelog;
        this.clusterSharding = clusterSharding;
        this.clusterSharding.init(
            Entity.of(
                ArtifactProcessorEntity.ENTITY_TYPE_KEY,
                ArtifactProcessorEntity::create
            )
        );
        this.persistentEntityRegistry = persistentEntityRegistry;
        this.securityConfig = securityConfig;
    }

    @Override
    public HeaderServiceCall<SonatypeData, String> processSonatypeData() {
        return (requestHeader, webhookData) -> {
            final var signatureOpt = requestHeader.getHeader(NEXUS_SECRET_HEADER);
            if (signatureOpt.isEmpty()) {
                final var unauthorized = new ResponseHeader(401, new MessageProtocol(), HashTreePMap.empty());
                return CompletableFuture.completedFuture(Pair.create(unauthorized, "Unauthorized"));
            }
            final String nexusSignature = signatureOpt.get();

            LOGGER.log(Level.INFO, "Webhook Content:" + webhookData.toString());
            if ("CREATED".equals(webhookData.action())) {

                final SonatypeComponent component = webhookData.component();
                final String groupId = component.group();
                return this.authorizeInvoke(this.artifacts.getArtifacts(groupId))
                    .invoke()
                    .thenCompose(response -> {
                        final MavenCoordinates coordinates = MavenCoordinates.parse(
                            groupId + ":" + component.name() + ":" + component.version());
                        if (response instanceof GetArtifactsResponse.ArtifactsAvailable) {
                            if (((GetArtifactsResponse.ArtifactsAvailable) response).artifactIds().contains(component.name())) {
                                return this.getProcessingEntity(coordinates)
                                    .ask(replyTo -> new ArtifactSagaCommand.StartProcessing(webhookData,
                                        coordinates,
                                        replyTo
                                    ), Duration.ofSeconds(10));
                            }
                        }
                        return CompletableFuture.completedStage(NotUsed.notUsed());
                    })
                    .thenApply(response -> Pair.create(ResponseHeader.OK, "success"));
            }
            return CompletableFuture.completedStage(Pair.create(ResponseHeader.OK, "success"));
        };
    }

    @Override
    public Topic<ScrapedArtifactEvent> topic() {
        return TopicProducer.singleStreamWithOffset(offset ->
            // Load the event stream for the passed in shard tag
            this.persistentEntityRegistry.eventStream(ScrapedArtifactEvent.TAG, offset));
    }


    public EntityRef<ArtifactSagaCommand> getProcessingEntity(final MavenCoordinates mavenCoordinates) {
        return this.clusterSharding.entityRefFor(ArtifactProcessorEntity.ENTITY_TYPE_KEY, mavenCoordinates.toString());
    }

    @Override
    public Config getSecurityConfig() {
        return this.securityConfig;
    }
}
