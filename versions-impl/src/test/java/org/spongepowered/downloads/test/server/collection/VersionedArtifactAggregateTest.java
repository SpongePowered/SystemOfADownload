package org.spongepowered.downloads.test.server.collection;


import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.versions.server.domain.ACEvent;
import org.spongepowered.downloads.versions.server.domain.State;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

public class VersionedArtifactAggregateTest {

    @Test
    public void emptyStateTransition() {
        final State empty = State.empty();
        Assertions.assertFalse(empty.isRegistered());
    }

    @Test
    public void emptyStateRegistration() {
        final State example = State.empty().register(
            new ACEvent.ArtifactCoordinatesUpdated(new ArtifactCoordinates("com.example", "example")));

        Assertions.assertTrue(example.isRegistered());
    }

    @Test
    public void stateRegistrationWithVersion() throws URISyntaxException {
        final ArtifactCoordinates exampleCoordinates = new ArtifactCoordinates("com.example", "example");
        State.ACState example = State.empty().register(new ACEvent.ArtifactCoordinatesUpdated(exampleCoordinates));
        final var exampleVersion = exampleCoordinates.version("0.0.1");
        final URL exampleJar = this.getClass().getClassLoader().getResource("test-jar.jar");
        Assumptions.assumeTrue(exampleJar != null);
        Assertions.assertNotNull(exampleJar, "Example jar is missing, needed for various tests");
        final var exampleArtifact = new Artifact(Optional.of("universal"), exampleJar.toURI(), "foo", "bar", ".jar");
        example = example.withAddedArtifacts(exampleVersion, List.of(exampleArtifact));
        Assertions.assertFalse(example.versionedArtifacts().isEmpty(), "Should have a new versioned artifact");
        final Option<List<Artifact>> artifacts = example.versionedArtifacts().get(exampleVersion.version);
        Assertions.assertFalse(artifacts.isEmpty(), "The artifact list for " + exampleVersion.version + " should be non-empty");
        Assertions.assertEquals(artifacts.get(), List.of(exampleArtifact), "List should be equal");

        final var exampleNoClassifier = new Artifact(Optional.empty(), exampleJar.toURI(), "foo", "bar", ".jar");
        example = example.withAddedArtifacts(exampleVersion, List.of(exampleNoClassifier));
        Assertions.assertFalse(example.versionedArtifacts().isEmpty(), "Should have a new versioned artifact");
        final Option<List<Artifact>> newArtifacts = example.versionedArtifacts().get(exampleVersion.version);
        Assertions.assertFalse(newArtifacts.isEmpty(), "The artifact list for " + exampleVersion.version + " should be non-empty");
        Assertions.assertEquals(newArtifacts.get(), List.of(exampleArtifact, exampleNoClassifier), "List should be equal");
    }

    @Test
    public void stateReordering() {
        final ArtifactCoordinates exampleCoordinates = new ArtifactCoordinates("com.example", "example");
        State.ACState example = State.empty().register(new ACEvent.ArtifactCoordinatesUpdated(exampleCoordinates));
        final var acEvents = example.addVersion(exampleCoordinates.version("0.0.1"));
        Assertions.assertEquals(acEvents.size(), 1, "Should have one event");
        final var zero1 = example.withVersion("0.0.1");
        final var newEvents = zero1.addVersion(exampleCoordinates.version("0.0.2"));
        Assertions.assertEquals(newEvents.size(), 1, "0.0.2 should be the only new event");
        final var zero3 = zero1.withVersion("0.0.3");
        final var addingZero2 = zero3.addVersion(exampleCoordinates.version("0.0.2"));
        Assertions.assertEquals(addingZero2.size(), 2, "Should have 2 events");
        Assertions.assertEquals(addingZero2.get(0), new ACEvent.ArtifactVersionRegistered(exampleCoordinates.version("0.0.2"), 1), "Should have the new event");
        Assertions.assertEquals(addingZero2.get(1), new ACEvent.ArtifactVersionMoved(exampleCoordinates.version("0.0.2"), 1, List.of(exampleCoordinates.version("0.0.3"))), "Should have the old event");
    }

}
