module org.spongepowered.downloads.artifacts.server {
    requires io.micronaut.micronaut_context;
    requires io.micronaut.micronaut_core;
    requires io.micronaut.data.micronaut_data_jdbc;
    requires io.micronaut.micronaut_http;
    requires io.micronaut.micronaut_inject;
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
    requires jakarta.annotation;
}
