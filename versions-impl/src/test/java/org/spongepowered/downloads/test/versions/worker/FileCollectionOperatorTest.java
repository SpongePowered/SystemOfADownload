package org.spongepowered.downloads.test.versions.worker;

import akka.actor.testkit.typed.javadsl.FishingOutcomes;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.vavr.collection.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.worker.actor.artifacts.CommitExtractor;
import org.spongepowered.downloads.versions.worker.actor.artifacts.FileCollectionOperator;
import org.spongepowered.downloads.versions.worker.actor.artifacts.PotentiallyUsableAsset;

import java.io.File;
import java.net.URISyntaxException;
import java.time.Duration;

public class FileCollectionOperatorTest {

    private TestKitJunitResource testKit;

    private static Config config;

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
    }

    @AfterEach
    public void teardown() {
        testKit.system().terminate();
    }

    @Test
    public void testKickoffFileCollection() throws URISyntaxException {
        final var commandProbe = this.testKit.<CommitExtractor.ChildCommand>createTestProbe();
        final var responseProbe = this.testKit.<CommitExtractor.AssetCommitResponse>createTestProbe();
        final var requestBehavior = FileCollectionOperator.scanJarFilesForCommit(
            commandProbe.ref(), responseProbe.ref());
        final var testJarPath = this.getClass().getClassLoader().getResource("test-jar.jar");
        Assertions.assertNotNull(testJarPath, "test-jar.jar is missing");
        final var spawn = this.testKit.spawn(requestBehavior);
        final var coordinates = MavenCoordinates.parse("com.example:example:1.0.0");
        final var asset = new PotentiallyUsableAsset(
            coordinates, ".jar", testJarPath.toURI());
        spawn.tell(new FileCollectionOperator.TryFindingCommitForFiles(List.of(asset), coordinates));
        commandProbe.fishForMessage(Duration.ofSeconds(10), msg -> {
            if (!(msg instanceof CommitExtractor.AttemptFileCommit afc)) {
                return FishingOutcomes.fail("got a different command");
            }
            afc.ref().tell(new CommitExtractor.NoCommitsFoundForFile(afc.asset()));
            return FishingOutcomes.complete();
        });
        responseProbe.fishForMessage(Duration.ofSeconds(10), msg -> {
            if (!(msg instanceof CommitExtractor.NoCommitsFoundForFile)) {
                return FishingOutcomes.fail("got a different response than test expected");
            }
            return FishingOutcomes.complete();
        });
    }
}
