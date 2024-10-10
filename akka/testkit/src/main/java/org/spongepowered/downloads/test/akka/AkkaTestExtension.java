package org.spongepowered.downloads.test.akka;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.BehaviorTestKit;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.SpawnProtocol;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.persistence.testkit.PersistenceTestKitPlugin;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;

@Factory
public class AkkaTestExtension {


    @Replaces
    @Bean
    public Behavior<SpawnProtocol.Command> testBehavior() {
        return SpawnProtocol.create();
    }

    @Replaces
    @Bean
    public Config testConfig() {
        return PersistenceTestKitPlugin.getInstance().config()
            .withFallback(BehaviorTestKit.applicationTestConfig())
            .withFallback(ConfigFactory.defaultApplication())
            .resolve();
    }

    @Singleton
    @Bean(preDestroy = "shutdownTestKit")
    public ActorTestKit testKit(final @NonNull Config config) {
        return ActorTestKit.create(config);
    }

    @Bean
    @Replaces(bean = ClusterSharding.class)
    public ClusterSharding cluster(final ActorSystem<?> system) {
        return ClusterSharding.get(system);
    }

    @Replaces(bean = ActorSystem.class)
    @Singleton
    public ActorSystem<?> system(@NonNull ActorTestKit kit) {
        return kit.system();
    }

}
