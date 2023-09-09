package org.spongepowered.downloads.test.akka;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.BehaviorTestKit;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.SpawnProtocol;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

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
        return ConfigFactory.defaultApplication()
            .withFallback(BehaviorTestKit.applicationTestConfig())
            .resolve();
    }

    @Bean(preDestroy = "shutdownTestKit")
    public ActorTestKit testKit() {
        return ActorTestKit.create();
    }

    @Replaces(bean = ActorSystem.class)
    @Singleton
    public ActorSystem<?> system(@NonNull ActorTestKit kit) {
        return kit.system();
    }

}
