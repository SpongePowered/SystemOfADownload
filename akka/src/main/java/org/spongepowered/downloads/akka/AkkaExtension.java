package org.spongepowered.downloads.akka;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.Scheduler;
import akka.actor.typed.SpawnProtocol;
import akka.actor.typed.javadsl.Adapter;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

@Factory
public class AkkaExtension {

    @Bean
    public Scheduler systemScheduler(@NonNull ActorSystem<?> system) {
        return system.scheduler();
    }

    @Bean
    public Config akkaConfig() {
        return ConfigFactory.defaultApplication();
    }

    @Singleton
    @Bean(preDestroy = "terminate")
    public ActorSystem<?> system(@NonNull Behavior<?> behavior, @NonNull Config config) {
        return ActorSystem.create(behavior, "soad-master");
    }

    @Bean
    @Singleton
    public ClusterSharding clusterSharding(@NonNull ActorSystem<?> system) {
        return ClusterSharding.get(system);
    }

}
