package org.spongepowered.downloads.artifacts.server;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.SpawnProtocol;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import io.micronaut.context.annotation.Factory;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Factory
public class Application {

    private final ActorSystem<?> system;
    private final ClusterSharding sharding;

    @Inject
    public Application(
        final ActorSystem<?> system,
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
