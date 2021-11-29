package org.spongepowered.downloads.test.versions.worker;

import akka.Done;
import akka.actor.testkit.typed.javadsl.FishingOutcomes;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.receptionist.Receptionist;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.vavr.collection.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.worker.actor.artifacts.FileCollectionOperator;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.ArtifactEvent;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.ArtifactState;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.VersionedArtifactCommand;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.VersionedArtifactEntity;

import java.io.File;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;

public class VersionedArtifactEntityTest {

    private TestKitJunitResource testKit;
    private EventSourcedBehaviorTestKit<VersionedArtifactCommand, ArtifactEvent, ArtifactState> eventSourcedTestKit;

    private static Config config;
    private TestProbe<FileCollectionOperator.Request> fileOperatorProbe;

    @BeforeAll
    public static void setupConfig() {
        final var resource = VersionedArtifactEntityTest.class.getClassLoader().getResource("application-test.conf");
        Assertions.assertNotNull(resource);
        final var file = new File(resource.getFile());
        config = ConfigFactory.parseFile(file);
    }

    @BeforeEach
    public void setup() {
        this.testKit = new TestKitJunitResource(config.resolve() // Resolve the config first
            .withFallback(EventSourcedBehaviorTestKit.config()));
        testKit.system().log().info("Starting test");
        final var probe = this.testKit.createTestProbe(ClusterSharding.ShardCommand.class);

        final var ctx = new EntityContext<>(
            VersionedArtifactEntity.ENTITY_TYPE_KEY,
            "com.example:example:1.0.0",
            probe.ref()
        );
        this.fileOperatorProbe = this.testKit.createTestProbe();
        this.testKit.system().receptionist().tell(Receptionist.register(FileCollectionOperator.KEY, fileOperatorProbe.ref()));

        this.eventSourcedTestKit =
            EventSourcedBehaviorTestKit.create(testKit.system(), VersionedArtifactEntity.create(ctx));
    }

    @AfterEach
    public void teardown() {
        testKit.system().log().info("Finishing test");
        testKit.system().terminate();
    }

    @Test
    public void testEmptyState() {
        final var state = this.eventSourcedTestKit.getState();
        Assertions.assertTrue(state.artifacts().isEmpty());
        Assertions.assertTrue(state.repositories().isEmpty());
        Assertions.assertFalse(state.needsArtifactScan());
        Assertions.assertFalse(state.needsCommitResolution());
    }

    @Test
    public void testRegistration() {
        final var state = this.eventSourcedTestKit
            .<Done>runCommand(ref -> new VersionedArtifactCommand.Register(
                MavenCoordinates.parse("com.example:example:1.0.0"), ref)
            )
            .state();
        Assertions.assertTrue(state.artifacts().isEmpty());
        Assertions.assertTrue(state.repositories().isEmpty());
        Assertions.assertFalse(state.needsArtifactScan());
        Assertions.assertFalse(state.needsCommitResolution());
    }

    @Test
    public void testAssetRegistration() throws URISyntaxException {
        this.eventSourcedTestKit
            .<Done>runCommand(ref -> new VersionedArtifactCommand.Register(
                MavenCoordinates.parse("com.example:example:1.0.0"), ref)
            )
            .state();
        final var testJarPath = this.getClass().getClassLoader().getResource("test-jar.jar");
        Assertions.assertNotNull(testJarPath, "test-jar.jar isn't available, should be checked in");
        final var downloadUrl = testJarPath.toURI();
        final var artifact = new Artifact(Optional.empty(), downloadUrl, "test-jar.jar", "jar", "jar");
        final var newState = this.eventSourcedTestKit
            .<Done>runCommand(ref -> new VersionedArtifactCommand.AddAssets(List.of(artifact), ref))
            .state();
        this.fileOperatorProbe.fishForMessage(Duration.ofSeconds(10), r -> {
            if (!(r instanceof FileCollectionOperator.TryFindingCommitForFiles tfcf)) {
                return FishingOutcomes.fail("expected a try to commit");
            }
            final var files = tfcf.files();
            if (files.size() != 1) {
                return FishingOutcomes.fail("Expected 1 file only");
            }
            final var file = files.get(0);
            if (!file.downloadURL().equals(downloadUrl)) {
                return FishingOutcomes.fail("Expected the same download url");
            }
            return FishingOutcomes.complete();
        });
        Assertions.assertFalse(newState.artifacts().isEmpty());
        Assertions.assertTrue(newState.repositories().isEmpty());
        Assertions.assertTrue(newState.needsArtifactScan());
        Assertions.assertFalse(newState.needsCommitResolution());
    }

}

