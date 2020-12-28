package org.spongepowered.downloads.webhook;


import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import io.vavr.collection.HashMap;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.changelog.api.ChangelogService;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

public class SonatypeWebhookServiceImpl implements SonatypeWebhookService {

    public static final String TOPIC_NAME = "artifact-changelog-analysis";
    private final ArtifactService artifacts;
    private final ChangelogService changelog;
    private final PersistentEntityRegistry registry;

    @Inject
    public SonatypeWebhookServiceImpl(
        final ArtifactService artifacts, final ChangelogService changelog,
        final PersistentEntityRegistry registry
    ) {
        this.artifacts = artifacts;
        this.changelog = changelog;
        this.registry = registry;
        registry.register(ArtifactProcessorEntity.class);
    }

    @Override
    public ServiceCall<SonatypeData, NotUsed> processSonatypeData() {
        return (webhook) -> {
            if ("CREATED".equals(webhook.action())) {

                final SonatypeComponent component = webhook.component();
                final var collection = new ArtifactCollection(HashMap.empty(),
                    new Group(component.group(), component.group(), ""), component.id(),
                    component.version()
                );
                return this.artifacts.registerArtifacts()
                    .invoke(new ArtifactRegistration.RegisterCollection(collection))
                    .thenCompose(response -> {
                        if (response instanceof ArtifactRegistration.Response.RegisteredArtifact registered) {
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
        };
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
}
