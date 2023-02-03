package systemofadownload;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Scheduler;
import akka.actor.typed.SpawnProtocol;
import akka.actor.typed.javadsl.Adapter;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.ClusterShardingSettings;
import akka.cluster.sharding.typed.ShardingEnvelope;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class AkkaExtension {


    @Bean
    public Scheduler systemScheduler() {
        return system().scheduler();
    }

    @Bean
    public Config akkaConfig() {
        return ConfigFactory.load();
    }

    @Bean(preDestroy = "terminate")
    public ActorSystem<SpawnProtocol.Command> system() {
        Config config = akkaConfig();
        return ActorSystem.create(
            Behaviors.setup(ctx -> {
                akka.actor.ActorSystem unTypedSystem = Adapter.toClassic(ctx.getSystem());
                AkkaManagement.get(unTypedSystem).start();
                ClusterBootstrap.get(unTypedSystem).start();
                return SpawnProtocol.create();
            }), config.getString("some.cluster.name"));
    }

    @Bean
    public ClusterSharding clusterSharding() {
        return ClusterSharding.get(system());
    }

    @Bean
    public ActorRef<ShardingEnvelope<SystemofadownloadController.Command>> someShardRegion() {
        return clusterSharding().init(Entity.of(null, null));
    }
}
