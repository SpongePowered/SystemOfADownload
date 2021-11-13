package org.spongepowered.downloads.test.versions.worker;

import akka.Done;
import akka.actor.testkit.typed.javadsl.FishingOutcomes;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import io.vavr.collection.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.versions.worker.consumer.CommitDetailsRegistrar;
import org.spongepowered.downloads.versions.worker.jgit.CommitResolver;

import java.net.URI;
import java.time.Duration;

public class CommitResolverTest {

    private TestKitJunitResource testKit;

    @BeforeEach
    public void setup() {
        this.testKit = new TestKitJunitResource();
        testKit.system().log().info("Starting test");
    }

    @AfterEach
    public void teardown() {
        testKit.system().log().info("Finishing test");
        testKit.system().terminate();
    }

    @Test
    public void testCommitResolver() {
        final var teller = testKit.createTestProbe(CommitDetailsRegistrar.Command.class);
        final var actor = testKit.spawn(CommitResolver.resolveCommit(teller.ref()));
        final var coords = new ArtifactCoordinates("com.example", "test").version("1.0");
        final var commit = "d838fee5d8e834ba9fd4d1c4fe0f8214d6dc90fc";
        final var url = "https://github.com/spongepowered/configurate.git";
        final var uri = List.of(URI.create(url));
        final var probe = testKit
            .<Done>createTestProbe();
        final var replyTo = probe
            .ref();
        actor.tell(new CommitResolver.ResolveCommitDetails(
            coords, commit, uri, replyTo
        ));
        teller.fishForMessage(Duration.ofSeconds(60), m -> {
            ((CommitDetailsRegistrar.HandleVersionedCommitReport) m).replyTo().tell(Done.getInstance());
            return FishingOutcomes.complete();
        });

        probe.fishForMessage(Duration.ofSeconds(60), m -> FishingOutcomes.complete());
    }

    @Test
    public void verifyNonExistentRepo() {
        final var teller = testKit.createTestProbe(CommitDetailsRegistrar.Command.class);
        final var actor = testKit.spawn(CommitResolver.resolveCommit(teller.ref()));
        final var coords = new ArtifactCoordinates("com.example", "test").version("1.0");
        final var commit = "d838fee5d8e834ba9fd4d1c4fe0f8214d6dc90fc";
        final var url = "https://example.com/git/doesnt-exist.git";
        final var uri = List.of(URI.create(url));
        final var probe = testKit
            .<Done>createTestProbe();
        final var replyTo = probe
            .ref();
        actor.tell(new CommitResolver.ResolveCommitDetails(
            coords, commit, uri, replyTo
        ));
        probe.fishForMessage(Duration.ofSeconds(10), m -> FishingOutcomes.complete());
    }


    @Test
    public void verifyNonExistentCommit() {
        final var teller = testKit.createTestProbe(CommitDetailsRegistrar.Command.class);
        final var actor = testKit.spawn(CommitResolver.resolveCommit(teller.ref()));
        final var coords = new ArtifactCoordinates("com.example", "test").version("1.0");
        final var commit = "a830fee5d8e894ba9aa4a1a4fe0f8214d6dc90fc";
        final var url = "https://github.com/spongepowered/configurate.git";
        final var uri = List.of(URI.create(url));
        final var probe = testKit
            .<Done>createTestProbe();
        final var replyTo = probe
            .ref();
        actor.tell(new CommitResolver.ResolveCommitDetails(
            coords, commit, uri, replyTo
        ));
        probe.fishForMessage(Duration.ofSeconds(60), m -> FishingOutcomes.complete());
    }
}
