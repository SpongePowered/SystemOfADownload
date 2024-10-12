package org.spongepowered.downloads.events.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.spongepowered.downloads.events.EventMarker;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
public abstract class OutboxRepository implements ReactiveStreamsPageableRepository<OutboxEvent, Long> {
    @Inject
    private ObjectMapper om;

    @Override
    public abstract <S extends OutboxEvent> Publisher<S> saveAll(Iterable<S> entities);

    public Mono<Void> saveAll(final List<? extends EventMarker> event) {
        return Flux.fromStream(() -> {
                final var outboxEvents = new ArrayList<OutboxEvent>();
                try {
                    for (final var e : event) {
                        outboxEvents.add(new OutboxEvent(e.topic(), e.partitionKey(), om.writeValueAsString(e)));
                    }
                } catch (final JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                return outboxEvents.stream();
            })
            .collectList()
            .flatMap(f -> Flux.from(this.saveAll(f)).then());
    }

}
