package org.spongepowered.downloads.artifacts.worker;


import akka.actor.typed.ActorSystem;
import akka.actor.typed.SpawnProtocol;
import akka.actor.typed.javadsl.AskPattern;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;

@Singleton
public class Application implements ApplicationEventListener<ServerStartupEvent> {

    @Inject
    private ActorSystem<SpawnProtocol.Command> system;

    public static void main(final String[] args) {
        Micronaut.run(Application.class, args);
    }

    @Override
    public void onApplicationEvent(final ServerStartupEvent event) {
        AskPattern.ask(this.system,
            response -> new SpawnProtocol.Spawn(),
            Duration.ofSeconds(10),
            this.system.scheduler()
            );
    }
}
