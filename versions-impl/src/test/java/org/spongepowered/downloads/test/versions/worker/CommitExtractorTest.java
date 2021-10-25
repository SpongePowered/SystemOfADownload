package org.spongepowered.downloads.test.versions.worker;


import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.worker.actor.CommitExtractor;
import org.spongepowered.downloads.versions.worker.actor.VersionedAssetWorker;

import java.net.URISyntaxException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CommitExtractorTest {

    public static final TestKitJunitResource testkit = new TestKitJunitResource(EventSourcedBehaviorTestKit.config());

    @Test
    public void VerifyCommitExtraction() throws URISyntaxException {
        final TestProbe<CommitExtractor.AssetCommitResponse> replyTo = testkit.createTestProbe();
        final var extractor = testkit.spawn(CommitExtractor.extractCommitFromAssets());
        final var coordinates = MavenCoordinates.parse("org.spongepowered.downloads:test-bin:0.0.1");

        final var testJarPath = this.getClass().getClassLoader().getResource("test-jar.jar");
        Assertions.assertNotNull(testJarPath, "test-jar.jar is null");
        final var asset = new VersionedAssetWorker.PotentiallyUsableAsset(coordinates, ".jar", testJarPath.toURI());
        extractor.tell(new CommitExtractor.AttemptFileCommit(asset, "not-used", replyTo.ref()));

        replyTo.expectMessage(new CommitExtractor.DiscoveredCommitFromFile("d838fee5d8e834ba9fd4d1c4fe0f8214d6dc90fc", asset));
    }

    @Test
    public void InvalidCommitFailure() throws URISyntaxException {
        final TestProbe<CommitExtractor.AssetCommitResponse> replyTo = testkit.createTestProbe();
        final var extractor = testkit.spawn(CommitExtractor.extractCommitFromAssets());
        final var coordinates = MavenCoordinates.parse("org.spongepowered.downloads:test-bin:0.0.1");
        final var testJarPath = this.getClass().getClassLoader().getResource("bad-commit-test-jar.jar");
        Assertions.assertNotNull(testJarPath, "bad-commit-test-jar.jar is null");
        final var asset = new VersionedAssetWorker.PotentiallyUsableAsset(coordinates, ".jar", testJarPath.toURI());
        extractor.tell(new CommitExtractor.AttemptFileCommit(asset, "not-used", replyTo.ref()));

        replyTo.expectMessageClass(CommitExtractor.FailedToRetrieveCommit.class);
    }

    @Test
    public void NoCommitFailure() throws URISyntaxException {
        final TestProbe<CommitExtractor.AssetCommitResponse> replyTo = testkit.createTestProbe();
        final var extractor = testkit.spawn(CommitExtractor.extractCommitFromAssets());
        final var coordinates = MavenCoordinates.parse("org.spongepowered.downloads:test-bin:0.0.1");
        final var testJarPath = this.getClass().getClassLoader().getResource("no-commit-test-jar.jar");
        Assertions.assertNotNull(testJarPath, "no-commit-test-jar.jar is null");
        final var asset = new VersionedAssetWorker.PotentiallyUsableAsset(coordinates, ".jar", testJarPath.toURI());
        extractor.tell(new CommitExtractor.AttemptFileCommit(asset, "not-used", replyTo.ref()));

        replyTo.expectMessageClass(CommitExtractor.FailedToRetrieveCommit.class);
    }

}
