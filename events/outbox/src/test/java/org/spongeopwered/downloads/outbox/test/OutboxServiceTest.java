package org.spongeopwered.downloads.outbox.test;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.spongepowered.downloads.events.outbox.OutboxRepository;
import org.spongepowered.downloads.events.outbox.OutboxService;
import reactor.core.publisher.Flux;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(startApplication = false)
public class OutboxServiceTest {

    @Inject
    OutboxService outboxService;

    @Inject
    OutboxRepository outboxRepository;

    @Test
    void testPublishEvents() {
        final var events = new ArrayList<DemoEvent>();
        for (int i = 0; i < 10; i++) {
            events.add(new DemoEvent("foo" + i, "bar" + i));
        }
        this.outboxRepository.saveAll(events).block();
        this.outboxService.publishOutboxEvents().block();
        final var afterPublish = Flux.from(this.outboxRepository.findAll()).collectList().block();
        assertTrue(afterPublish.isEmpty());
    }
}
