package org.spongepowered.downloads.webhook;


import javax.inject.Inject;

import akka.NotUsed;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.broker.kafka.KafkaProperties;
import com.lightbend.lagom.javadsl.api.transport.Method;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import io.vavr.collection.HashMap;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.changelog.api.ChangelogService;

import java.util.concurrent.CompletableFuture;

public class SonatypeWebhookService implements Service {

    public static final String TOPIC_NAME = "artifact-changelog-analysis";
    private final ArtifactService artifacts;
    private final ChangelogService changelog;
    private final PersistentEntityRegistry registry;

    @Inject
    public SonatypeWebhookService(
        final ArtifactService artifacts, final ChangelogService changelog,
        final PersistentEntityRegistry registry
    ) {
        this.artifacts = artifacts;
        this.changelog = changelog;
        this.registry = registry;
        registry.register(ArtifactProcessorEntity.class);
    }

    ServiceCall<SonatypeData, NotUsed> processSonatypeData() {
        return (webhook) -> {
            if ("CREATED".equals(webhook.action)) {
                final SonatypeComponent component = webhook.component;
                final var collection = new ArtifactCollection(HashMap.empty(), new Group(component.group, component.group, ""), component.id,
                    component.version);
                return this.artifacts.registerArtifacts()
                    .invoke(new ArtifactRegistration.RegisterCollection(collection))
                    .thenCompose(response -> {
                        if (response instanceof ArtifactRegistration.Response.RegisteredArtifact registered) {
                            return this.getProcessingEntity(registered.artifact().getMavenCoordinates())
                                .ask(new ArtifactProcessorEntity.Command.StartProcessing(webhook, registered.artifact()));
                        }
                        return CompletableFuture.completedStage(NotUsed.notUsed());
                    })
                    .thenApply(response -> NotUsed.notUsed());
            }
            return CompletableFuture.completedStage(NotUsed.notUsed());
        };
    }

    public Topic<ArtifactProcessorEntity.Event> topic() {
        return TopicProducer.singleStreamWithOffset(offset ->
            // Load the event stream for the passed in shard tag
            this.registry.eventStream(ArtifactProcessorEntity.Event.TAG, offset));
    }

    @JsonDeserialize
    static record SonatypeData(String timestamp, String nodeId, String initiator, String repositoryName, String action, SonatypeComponent component) {}
    @JsonDeserialize
    static record SonatypeComponent(String id, String componentId, String format, String name, String group, String version) {}
    @Override
    public Descriptor descriptor() {
        return Service.named("webhooks")
            .withCalls(
                Service.restCall(Method.POST, "/api/webhook", this::processSonatypeData)
            )
            .withTopics(
                Service.topic(TOPIC_NAME, this::topic)
                .withProperty(KafkaProperties.partitionKeyStrategy(), ArtifactProcessorEntity.Event::mavenCoordinates)
            );
    }

    PersistentEntityRef<ArtifactProcessorEntity.Command> getProcessingEntity(final String mavenCoordinates) {
        return this.registry.refFor(ArtifactProcessorEntity.class, mavenCoordinates);
    }}
