package org.spongepowered.downloads.test.artifacts.server.cmd.details;

import akka.NotUsed;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import akka.persistence.typed.PersistenceId;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifacts.events.DetailsEvent;
import org.spongepowered.downloads.artifacts.server.cmd.details.ArtifactDetailsEntity;
import org.spongepowered.downloads.artifacts.server.cmd.details.DetailsCommand;
import org.spongepowered.downloads.artifacts.server.cmd.details.state.DetailsState;
import org.spongepowered.downloads.artifacts.server.cmd.details.state.PopulatedState;
import org.testcontainers.utility.TestEnvironment;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ArtifactDetailsTest implements BeforeEachCallback {

    private static final Config appConfig = ConfigFactory.load().withFallback(ConfigFactory.defaultApplication());
    private static final ActorTestKit testKit = ActorTestKit.create(EventSourcedBehaviorTestKit.config().withFallback(appConfig.resolve()));

    @Inject
    TestEnvironment environment;

    private static final EventSourcedBehaviorTestKit<DetailsCommand, DetailsEvent, DetailsState> behaviorTestKit =
        EventSourcedBehaviorTestKit.create(
            testKit.system(),
            ArtifactDetailsEntity.create(
                new EntityContext<>(ArtifactDetailsEntity.ENTITY_TYPE_KEY, "org.spongepowered:example",
                    testKit.<ClusterSharding.ShardCommand>createTestProbe().ref()
                ), "org.spongepowered:example", PersistenceId.of("DetailsEntity", "org.spongepowered:example"))
        );

    @Override
    public void beforeEach(final ExtensionContext context) {
        behaviorTestKit.clear();
    }

    @Test
    public void testAndPopulate() {
        final var coordinates = new ArtifactCoordinates("org.spongepowered", "example");
        final var example = behaviorTestKit.<NotUsed>runCommand(
            replyTo -> new DetailsCommand.RegisterArtifact(coordinates, "Example", replyTo));
        Assertions.assertEquals(NotUsed.notUsed(), example.reply());
        // This verifies that the populated state is valid, not an empty state
        Assertions.assertEquals(new PopulatedState(coordinates, "Example", "", "", ""), example.state());
    }

    @Test
    public void testReRegister() {
        final var coordinates = new ArtifactCoordinates("org.spongepowered", "example");
        final var unused = behaviorTestKit.<NotUsed>runCommand(
            replyTo -> new DetailsCommand.RegisterArtifact(coordinates, "Example", replyTo));
        final var obscure = new ArtifactCoordinates("com.example", "somethingelse");
        final var example = behaviorTestKit.<NotUsed>runCommand(
            replyTo -> new DetailsCommand.RegisterArtifact(obscure, "replaced", replyTo));
        Assertions.assertEquals(NotUsed.notUsed(), example.reply());
        Assertions.assertEquals(new PopulatedState(coordinates, "Example", "", "", ""), example.state());
        Assertions.assertEquals(0, example.events().size());
        Assertions.assertEquals(2, unused.events().size());
    }

}
