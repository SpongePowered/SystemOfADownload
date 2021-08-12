package org.spongepowered.downloads.artifact.test.global;

import akka.Done;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import akka.persistence.typed.PersistenceId;
import com.typesafe.config.ConfigFactory;
import io.vavr.collection.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.artifact.global.GlobalCommand;
import org.spongepowered.downloads.artifact.global.GlobalEvent;
import org.spongepowered.downloads.artifact.global.GlobalRegistration;
import org.spongepowered.downloads.artifact.global.GlobalState;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GlobalArtifactsTest implements BeforeEachCallback {

    // If actor refs are being used, the testkit needs to have the akka modules
    // locally.
    public static final TestKitJunitResource testkit = new TestKitJunitResource(ConfigFactory.parseString(
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

    private final EventSourcedBehaviorTestKit<GlobalCommand, GlobalEvent, GlobalState> behaviorKit = EventSourcedBehaviorTestKit.create(
        testkit.system(), GlobalRegistration.create(
            "org.spongepowered",
            PersistenceId.of("GlobalEntity", "org.spongepowered")
        ));


    @Override
    public void beforeEach(final ExtensionContext context) {
        this.behaviorKit.clear();
    }

    @Test
    public void verifyGlobalRegistration() {
        final var unhandledMessageProbe = testkit.createUnhandledMessageProbe();
        final var spongePowered = new Group("org.spongepowered", "SpongePowered", "https://spongepowered.org/");
        final var result = behaviorKit.<Done>runCommand(
            replyTo -> new GlobalCommand.RegisterGroup(replyTo, spongePowered));
        Assertions.assertEquals(Done.done(), result.reply());
        Assertions.assertEquals(new GlobalState(List.of(spongePowered)), result.state());
        unhandledMessageProbe.expectNoMessage();
    }

    @Test
    public void validateDoubleRegistration() {
        final var unhandledMessageProbe = testkit.createUnhandledMessageProbe();
        final var spongePowered = new Group("org.spongepowered", "SpongePowered", "https://spongepowered.org/");
        final var result = behaviorKit.<Done>runCommand(
            replyTo -> new GlobalCommand.RegisterGroup(replyTo, spongePowered));
        Assertions.assertEquals(Done.done(), result.reply());
        Assertions.assertEquals(new GlobalState(List.of(spongePowered)), result.state());
        unhandledMessageProbe.expectNoMessage();
        final var duplicatedRegistration = behaviorKit.<Done>runCommand(
            replyTo -> new GlobalCommand.RegisterGroup(replyTo, spongePowered));
        Assertions.assertEquals(Done.done(), duplicatedRegistration.reply());
        Assertions.assertTrue(duplicatedRegistration.hasNoEvents());
        Assertions.assertEquals(new GlobalState(List.of(spongePowered)), duplicatedRegistration.state());
    }

}
