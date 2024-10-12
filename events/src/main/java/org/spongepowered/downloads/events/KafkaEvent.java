package org.spongepowered.downloads.events;

public record KafkaEvent(String topic, String key, Object payload) {
}
