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
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import io.vavr.collection.HashMap;
import org.pac4j.core.config.Config;
import org.pac4j.lagom.javadsl.SecuredService;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.auth.api.SOADAuth;
import org.spongepowered.downloads.changelog.api.ChangelogService;
import org.spongepowered.downloads.utils.AuthUtils;
import org.taymyr.lagom.javadsl.openapi.AbstractOpenAPIService;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

public class SonatypeWebhookServiceImpl extends AbstractOpenAPIService implements SonatypeWebhookService,
    SecuredService {

    public static final String TOPIC_NAME = "artifact-changelog-analysis";
    private final ArtifactService artifacts;
    private final ChangelogService changelog;
    private final PersistentEntityRegistry registry;
    private final Config securityConfig;

    @Inject
    public SonatypeWebhookServiceImpl(
        final ArtifactService artifacts, final ChangelogService changelog,
        final PersistentEntityRegistry registry,
        @SOADAuth final Config securityConfig
    ) {
        this.artifacts = artifacts;
        this.changelog = changelog;
        this.registry = registry;
        registry.register(ArtifactProcessorEntity.class);
        this.securityConfig = securityConfig;
    }

    @Override
    public ServiceCall<SonatypeData, NotUsed> processSonatypeData() {
        return this.authorize(AuthUtils.Types.WEBHOOK, AuthUtils.Roles.WEBHOOK, profile -> (webhook) -> {
            if ("CREATED".equals(webhook.action())) {

                final SonatypeComponent component = webhook.component();
                final var collection = new ArtifactCollection(HashMap.empty(),
                    new Group(component.group(), component.group(), ""), component.id(),
                    component.version()
                );
                return this.artifacts.registerArtifacts(component.group())
                    .invoke(new ArtifactRegistration.RegisterArtifact(component.id(), component.name(), component.version()))
                    .thenCompose(response -> {
                        if (response instanceof ArtifactRegistration.Response.RegisteredArtifact) {
                            final var registered = (ArtifactRegistration.Response.RegisteredArtifact) response;
                            return this.getProcessingEntity(registered.artifact().getMavenCoordinates())
                                .ask(new ArtifactProcessorEntity.Command.StartProcessing(webhook,
                                    registered.artifact()
                                ));
                        }
                        return CompletableFuture.completedStage(NotUsed.notUsed());
                    })
                    .thenApply(response -> NotUsed.notUsed());
            }
            return CompletableFuture.completedStage(NotUsed.notUsed());
        });
    }

    @Override
    public Topic<ScrapedArtifactEvent> topic() {
        return TopicProducer.singleStreamWithOffset(offset ->
            // Load the event stream for the passed in shard tag
            this.registry.eventStream(ScrapedArtifactEvent.TAG, offset));
    }


    public PersistentEntityRef<ArtifactProcessorEntity.Command> getProcessingEntity(final String mavenCoordinates) {
        return this.registry.refFor(ArtifactProcessorEntity.class, mavenCoordinates);
    }

    @Override
    public Config getSecurityConfig() {
        return this.securityConfig;
    }
}
