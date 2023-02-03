package org.spongepowered.downloads.artifacts.server;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.info.*;

@OpenAPIDefinition(
    info = @Info(
            title = "artifacts",
            version = "0.0"
    )
)
public class Application implements ApplicationEventListener<ServerStartupEvent> {

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
