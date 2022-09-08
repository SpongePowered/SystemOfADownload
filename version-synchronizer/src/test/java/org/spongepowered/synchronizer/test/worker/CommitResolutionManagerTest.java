package org.spongepowered.synchronizer.test.worker;

import akka.Done;
import akka.actor.testkit.typed.javadsl.FishingOutcomes;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import io.vavr.collection.List;
import org.eclipse.jgit.util.SystemReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.synchronizer.actor.CommitDetailsRegistrar;
import org.spongepowered.synchronizer.gitmanaged.util.jgit.CommitResolutionManager;

import java.io.File;
import java.net.URI;
import java.time.Duration;

public class CommitResolutionManagerTest {

    private TestKitJunitResource testKit;

    @BeforeEach
    public void setup() {
        this.testKit = new TestKitJunitResource();
        testKit.system().log().info("Starting test");
        // Because jgit has to be told to ignore a lot of things about the system
        // we need to proxy
        final var dummyGitConfig = this.getClass().getClassLoader().getResource("dummy.gitconfig");
        Assertions.assertNotNull(dummyGitConfig, "dummy git config cannot be null");
        final var gitConfig = new File(dummyGitConfig.getFile());
        SystemReader.setInstance(new TestGitSystemReader(gitConfig));
    }

    @AfterEach
    public void teardown() {
        testKit.system().log().info("Finishing test");
        testKit.system().terminate();
    }

    @Test
    public void testCommitResolver() {
        final var teller = testKit.createTestProbe(CommitDetailsRegistrar.Command.class);
        final var actor = testKit.spawn(CommitResolutionManager.resolveCommit(teller.ref()));
        final var coords = new ArtifactCoordinates("com.example", "test").version("1.0");
        final var commit = "d838fee5d8e834ba9fd4d1c4fe0f8214d6dc90fc";
        final var url = "https://github.com/spongepowered/configurate.git";
        final var uri = List.of(URI.create(url));
        final var probe = testKit
            .<Done>createTestProbe();
        final var replyTo = probe
            .ref();
        actor.tell(new CommitResolutionManager.ResolveCommitDetails(
            coords, commit, uri, replyTo
        ));
        // This teller is the intermediary registrar that should receive HandleVersionedCommitReport
        // that ultimately tells the probe a Done.
        teller.fishForMessage(Duration.ofSeconds(60), m -> {
            ((CommitDetailsRegistrar.HandleVersionedCommitReport) m).replyTo().tell(Done.getInstance());
            return FishingOutcomes.complete();
        });

        probe.fishForMessage(Duration.ofSeconds(60), m -> FishingOutcomes.complete());
    }

    @Test
    public void verifyNonExistentRepo() {
        final var teller = testKit.createTestProbe(CommitDetailsRegistrar.Command.class);
        final var actor = testKit.spawn(CommitResolutionManager.resolveCommit(teller.ref()));
        final var coords = new ArtifactCoordinates("com.example", "test").version("1.0");
        final var commit = "d838fee5d8e834ba9fd4d1c4fe0f8214d6dc90fc";
        final var url = "https://example.com/git/doesnt-exist.git";
        final var uri = List.of(URI.create(url));
        final var probe = testKit
            .<Done>createTestProbe();
        final var replyTo = probe
            .ref();
        actor.tell(new CommitResolutionManager.ResolveCommitDetails(
            coords, commit, uri, replyTo
        ));
        probe.fishForMessage(Duration.ofSeconds(10), m -> FishingOutcomes.complete());
    }


    @Test
    public void verifyNonExistentCommit() {
        final var teller = testKit.createTestProbe(CommitDetailsRegistrar.Command.class);
        final var actor = testKit.spawn(CommitResolutionManager.resolveCommit(teller.ref()));
        final var coords = new ArtifactCoordinates("com.example", "test").version("1.0");
        final var commit = "a830fee5d8e894ba9aa4a1a4fe0f8214d6dc90fc";
        final var url = "https://github.com/spongepowered/configurate.git";
        final var uri = List.of(URI.create(url));
        final var probe = testKit
            .<Done>createTestProbe();
        final var replyTo = probe
            .ref();
        actor.tell(new CommitResolutionManager.ResolveCommitDetails(
            coords, commit, uri, replyTo
        ));
        probe.fishForMessage(Duration.ofSeconds(60), m -> FishingOutcomes.complete());
    }

    @Test
    public void verifyCommitFromTwoRepositories() {
        final var teller = testKit.createTestProbe(CommitDetailsRegistrar.Command.class);
        final var actor = testKit.spawn(CommitResolutionManager.resolveCommit(teller.ref()));
        final var coords = new ArtifactCoordinates("org.spongepowered", "spongevanilla").version("1.16.5-8.1.0-RC1184");
        final var commit = "6e443ec04ded4385d12c2e609360e81a770fbfcb";
        final var url = "https://github.com/spongepowered/spongevanilla.git";
        final var newUrl = "https://github.com/spongepowered/sponge.git";
        final var repos = List.of(URI.create(newUrl), URI.create(url));
        final var probe = testKit.<Done>createTestProbe();
        final var replyTo = probe.ref();
        actor.tell(new CommitResolutionManager.ResolveCommitDetails(
            coords, commit, repos, replyTo
        ));
        probe.fishForMessage(Duration.ofMinutes(10), m -> FishingOutcomes.complete());
    }
}
