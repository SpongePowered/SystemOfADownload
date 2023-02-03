package org.spongepowered.downloads.artifacts.server;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.SpawnProtocol;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.info.*;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.spongepowered.downloads.artifacts.server.global.GlobalManager;

@OpenAPIDefinition(
    info = @Info(
            title = "artifacts",
            version = "0.0"
    )
)
@Singleton
@Factory
public class Application {

    private final ActorSystem<SpawnProtocol.Command> system;
    private final ClusterSharding sharding;

    @Inject
    public Application(
        final ActorSystem<SpawnProtocol.Command> system,
        final ClusterSharding sharding
        ) {
        this.system = system;
        this.sharding = sharding;
    }

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }

    @EventListener
    public void onApplicationEvent(final ServerStartupEvent event) {

    }
}
