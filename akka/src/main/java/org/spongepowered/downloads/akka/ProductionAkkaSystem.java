package org.spongepowered.downloads.akka;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.SpawnProtocol;
import akka.actor.typed.javadsl.Behaviors;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;

@Factory
public class ProductionAkkaSystem {

    @Bean
    public Behavior<SpawnProtocol.Command> productionGuardian() {
        return Behaviors.<SpawnProtocol.Command>setup(ctx -> {
            final var system = ctx.getSystem();
            ClusterBootstrap.get(system).start();
            AkkaManagement.get(system).start();
            return SpawnProtocol.create();
        });
    }

}
