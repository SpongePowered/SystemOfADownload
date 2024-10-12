package org.spongepowered.downloads.events.outbox;

import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class OutboxService {

    @Inject private OutboxProducer publisher;
    @Inject private OutboxRepository repository;

    // This is scheduled every 5 seconds
    @Scheduled(cron = "0/5 * * * * ?")
    @Transactional
    public Mono<Void> publishOutboxEvents() {
        return Flux.from(this.repository.findAll())
            .flatMap(oe ->
                this.publisher.sendReactive(oe.topic(), oe.partitionKey(), oe.payload())
                    .then(Mono.from(this.repository.deleteById(oe.id())))
            ).then();
    }

}
