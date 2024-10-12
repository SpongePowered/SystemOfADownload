package org.spongepowered.downloads.events.outbox;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;

@MappedEntity(value = "outbox")
public record OutboxEvent(
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id,
    String topic,
    String partitionKey,
    @TypeDef(type = DataType.JSON)
    Object payload
) {

    public OutboxEvent(String topic, String partitionKey, Object payload) {
        this(null, topic, partitionKey, payload);
    }
}
