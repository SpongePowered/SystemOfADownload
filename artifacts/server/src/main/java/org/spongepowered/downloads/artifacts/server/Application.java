package org.spongepowered.downloads.artifacts.server;

import io.micronaut.context.annotation.Factory;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Factory
public class Application {

    @Inject
    public Application(
    ) {
    }

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }

    @EventListener
    public void onApplicationEvent(final ServerStartupEvent event) {
    }
}
