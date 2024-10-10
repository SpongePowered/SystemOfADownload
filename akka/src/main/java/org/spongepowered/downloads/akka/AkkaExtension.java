package org.spongepowered.downloads.akka;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.Scheduler;
import akka.actor.typed.SpawnProtocol;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

/**
 * The Akka extension for an application. This provides the {@link ActorSystem} and {@link ClusterSharding} for the
 * application.
 */
@Factory
public class AkkaExtension {

    /**
     * The {@link Scheduler} for the system.
     *
     * @param system The system to get the scheduler from
     * @return The scheduler
     */
    @Bean
    public Scheduler systemScheduler(@NonNull ActorSystem<?> system) {
        return system.scheduler();
    }

    /**
     * The {@link Config} for the system.
     *
     * @return The config
     */
    @Bean
    public Config akkaConfig() {
        return ConfigFactory.defaultApplication();
    }

    /**
     * The {@link ActorSystem} for the application.
     *
     * @param behavior The behavior to use for the system
     * @param config   The config to use for the system
     * @return The actor system
     */
    @Singleton
    @Bean(preDestroy = "terminate")
    public ActorSystem<?> system(@NonNull Behavior<?> behavior, @NonNull Config config) {
        return ActorSystem.create(behavior, "soad-master", config);
    }

    /**
     * The {@link ClusterSharding} for the application.
     *
     * @param system The system to get the sharding from
     * @return The cluster sharding
     */
    @Bean
    @Singleton
    public ClusterSharding clusterSharding(@NonNull ActorSystem<?> system) {
        return ClusterSharding.get(system);
    }

}
