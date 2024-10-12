module org.spongepowered.downloads.artifacts.server {
    requires io.micronaut.core;
    requires io.micronaut.data.micronaut_data_r2dbc;
    requires io.micronaut.http;
    requires io.micronaut.inject;
    requires io.micronaut.serde.micronaut_serde_api;
    requires jakarta.persistence;
    requires jakarta.validation;
    requires org.eclipse.jgit;
    requires org.spongepowered.downloads.artifacts.api;
    requires org.spongepowered.downloads.artifacts.events;
    requires org.spongepowered.downloads.events.outbox;
    requires io.micronaut.data.micronaut_data_tx;
    requires jakarta.inject;
    requires com.fasterxml.jackson.databind;
    requires io.micronaut.data.micronaut_data_model;
    requires reactor.core;
}
