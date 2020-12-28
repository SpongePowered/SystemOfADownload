package org.spongepowered.downloads.webhook;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.broker.kafka.KafkaProperties;
import com.lightbend.lagom.javadsl.api.transport.Method;
import io.swagger.v3.oas.annotations.Operation;

public interface SonatypeWebhookService extends Service {
    public static final String TOPIC_NAME = "artifact-changelog-analysis";

    @Operation(
        tags = "sonatype"
    )
    ServiceCall<SonatypeData, NotUsed> processSonatypeData();

    Topic<ScrapedArtifactEvent> topic();

    @Override
    default Descriptor descriptor() {
        return Service.named("webhooks")
            .withCalls(
                Service.restCall(Method.POST, "/api/webhook", this::processSonatypeData)
            )
            .withTopics(
                Service.topic(TOPIC_NAME, this::topic)
                    .withProperty(
                        KafkaProperties.partitionKeyStrategy(), ScrapedArtifactEvent::mavenCoordinates)
            );
    }
}
