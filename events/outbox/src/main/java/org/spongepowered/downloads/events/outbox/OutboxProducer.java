package org.spongepowered.downloads.events.outbox;

import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.KafkaKey;
import io.micronaut.configuration.kafka.annotation.Topic;
import reactor.core.publisher.Mono;

@KafkaClient(acks = KafkaClient.Acknowledge.ONE)
public interface OutboxProducer {

    <T> void publish(@Topic String topic, @KafkaKey String key, T payload);

    default <T> Mono<Void> sendReactive(String topic, String key, T payload) {
        return Mono.create(sink -> {
            this.publish(topic, key, payload);
            sink.success();
        });
    }
}
