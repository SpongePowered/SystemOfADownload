module org.spongepowered.downloads.events.outbox {

    exports org.spongepowered.downloads.events.outbox;

    requires transitive com.fasterxml.jackson.databind;
    requires io.micronaut.context;
    requires io.micronaut.data.micronaut_data_model;
    requires io.micronaut.data.micronaut_data_tx;
    requires io.micronaut.kafka.micronaut_kafka;
    requires transitive jakarta.inject;
    requires org.reactivestreams;
    requires reactor.core;
    requires transitive org.spongepowered.downloads.events;
    requires io.micronaut.data.micronaut_data_r2dbc;
    requires transitive jakarta.persistence;
}
