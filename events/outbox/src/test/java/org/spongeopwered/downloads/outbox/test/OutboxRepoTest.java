package org.spongeopwered.downloads.outbox.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.spongepowered.downloads.events.outbox.OutboxRepository;
import reactor.core.publisher.Flux;

import java.util.List;

@MicronautTest(startApplication = false, transactional = false)
public class OutboxRepoTest {

    @Inject
    OutboxRepository outboxRepository;

    @Test
    public void testSingleScan() {
        this.outboxRepository.saveAll(List.of(new DemoEvent("bar", "baz"))).block();
        final var outbox = Flux.from(this.outboxRepository.findAll()).collectList().block();
        assertNotNull(outbox);
        assertEquals(1, outbox.size());
    }
}
