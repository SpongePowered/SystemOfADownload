module org.spongepowered.downloads.app {
    exports org.spongepowered.downloads.api;

    requires akka.actor.typed;
    requires akka.http;
    requires akka.http.core;
    requires akka.stream;
    requires akka.actor;
    requires com.fasterxml.jackson.annotation;
    requires akka.http.marshallers.jackson;
    requires org.slf4j;
    requires org.hibernate.orm.core;
    requires java.persistence;
    requires io.vavr;
    requires com.fasterxml.jackson.databind;
    requires maven.artifact;
}
