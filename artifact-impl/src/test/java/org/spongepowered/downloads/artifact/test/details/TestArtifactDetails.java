package org.spongepowered.downloads.artifact.test.details;

import akka.NotUsed;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import akka.persistence.typed.PersistenceId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.details.ArtifactDetailsEntity;
import org.spongepowered.downloads.artifact.details.DetailsCommand;
import org.spongepowered.downloads.artifact.details.DetailsEvent;
import org.spongepowered.downloads.artifact.details.state.DetailsState;
import org.spongepowered.downloads.artifact.details.state.PopulatedState;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestArtifactDetails implements BeforeEachCallback {

    public static final TestKitJunitResource testkit = new TestKitJunitResource(EventSourcedBehaviorTestKit.config());
    private final EventSourcedBehaviorTestKit<DetailsCommand, DetailsEvent, DetailsState> testKit =
        EventSourcedBehaviorTestKit.create(
            testkit.system(),
            ArtifactDetailsEntity.create(
                "org.spongepowered:example", PersistenceId.of("DetailsEntity", "org.spongepowered:example"))
        );

    @Override
    public void beforeEach(final ExtensionContext context) {
        testKit.clear();
    }

    @Test
    public void testAndPopulate() {
        final var coordinates = new ArtifactCoordinates("org.spongepowered", "example");
        final var example = testKit.<NotUsed>runCommand(
            replyTo -> new DetailsCommand.RegisterArtifact(coordinates, "Example", replyTo));
        Assertions.assertEquals(NotUsed.notUsed(), example.reply());
        // This verifies that the populated state is valid, not an empty state
        Assertions.assertEquals(new PopulatedState(coordinates, "Example", "", "", ""), example.state());
    }

    @Test
    public void testReRegister() {
        final var coordinates = new ArtifactCoordinates("org.spongepowered", "example");
        final var unused = testKit.<NotUsed>runCommand(
            replyTo -> new DetailsCommand.RegisterArtifact(coordinates, "Example", replyTo));
        final var obscure = new ArtifactCoordinates("com.example", "somethingelse");
        final var example = testKit.<NotUsed>runCommand(
            replyTo -> new DetailsCommand.RegisterArtifact(obscure, "replaced", replyTo));
        Assertions.assertEquals(NotUsed.notUsed(), example.reply());
        Assertions.assertEquals(new PopulatedState(coordinates, "Example", "", "", ""), example.state());
        Assertions.assertEquals(0, example.events().size());
        Assertions.assertEquals(2, unused.events().size());
    }

}
