package org.spongepowered.downloads.webhook;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.broker.kafka.KafkaProperties;
import com.lightbend.lagom.javadsl.api.transport.Method;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.taymyr.lagom.javadsl.openapi.OpenAPIService;
import org.taymyr.lagom.javadsl.openapi.OpenAPIUtils;

@OpenAPIDefinition(
    info = @Info(
        title = "WebhookService",
        description = "General Webhook receiving service",
        contact = @Contact(
            name = "SpongePowered",
            url = "https://spongepowered.org/",
            email = "dev@spongepowered.org"
        ),
        license = @License(
            name = "MIT - The MIT License",
            url = "https://opensource.org/licenses/MIT"
        )
    ),
    tags = {
        @Tag(name = "webhook", description = "Webhook services"),
        @Tag(name = "sonatype", description = "Sonatype interactive services")
    }
)
public interface SonatypeWebhookService extends OpenAPIService {
    String TOPIC_NAME = "artifact-changelog-analysis";

    @Operation(
        tags = "sonatype",
        method = "POST"
    )
    ServiceCall<SonatypeData, NotUsed> processSonatypeData();

    Topic<ScrapedArtifactEvent> topic();

    @Override
    default Descriptor descriptor() {
        return OpenAPIUtils.withOpenAPI(Service.named("webhooks")
            .withCalls(
                Service.restCall(Method.POST, "/api/webhook", this::processSonatypeData)
            )
            .withTopics(
                Service.topic(TOPIC_NAME, this::topic)
                    .withProperty(
                        KafkaProperties.partitionKeyStrategy(), ScrapedArtifactEvent::mavenCoordinates)
            )
            .withAutoAcl(true)
        );
    }
}
