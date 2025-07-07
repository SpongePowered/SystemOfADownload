module org.spongepowered.downloads.events.outbox {

    exports org.spongepowered.downloads.events.outbox;

    requires transitive com.fasterxml.jackson.databind;
    requires io.micronaut.micronaut_context;
    requires io.micronaut.data.micronaut_data_model;
    requires io.micronaut.data.micronaut_data_tx;
    requires io.micronaut.kafka.micronaut_kafka;
    requires transitive jakarta.inject;
    requires org.reactivestreams;
    requires transitive org.spongepowered.downloads.events.api;
    requires io.micronaut.data.micronaut_data_jdbc;
    requires transitive jakarta.persistence;
}
