package org.spongepowered.downloads.artifact.test.akka;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import com.typesafe.config.ConfigFactory;

public class EventBehaviorTestkit {
    public static TestKitJunitResource createTestKit() {
        return new TestKitJunitResource(ConfigFactory.parseString(
                """
                akka.serialization.jackson {
                    # The Jackson JSON serializer will register these modules.
                    jackson-modules += "akka.serialization.jackson.AkkaJacksonModule"
                    jackson-modules += "akka.serialization.jackson.AkkaTypedJacksonModule"
                    # AkkaStreamsModule optionally included if akka-streams is in classpath
                    jackson-modules += "akka.serialization.jackson.AkkaStreamJacksonModule"
                    jackson-modules += "com.fasterxml.jackson.module.paramnames.ParameterNamesModule"
                    jackson-modules += "com.fasterxml.jackson.datatype.jdk8.Jdk8Module"
                    jackson-modules += "com.fasterxml.jackson.module.scala.DefaultScalaModule"
                    jackson-modules += "io.vavr.jackson.datatype.VavrModule"
                }
                """
            )
            .resolve() // Resolve the config first
            .withFallback(EventSourcedBehaviorTestKit.config()));
    }
}
