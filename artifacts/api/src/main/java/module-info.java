module org.spongepowered.downloads.artifacts.api {
    exports org.spongepowered.downloads.artifact.api;
    exports org.spongepowered.downloads.artifact.api.query;
    exports org.spongepowered.downloads.artifact.api.mutation;
    exports org.spongepowered.downloads.artifact.api.registration;

    requires transitive com.fasterxml.jackson.databind;
    requires jakarta.validation;
    requires io.micronaut.micronaut_core;
}
