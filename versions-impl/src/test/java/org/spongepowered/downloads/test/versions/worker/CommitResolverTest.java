package org.spongepowered.downloads.test.versions.worker;

import akka.actor.testkit.typed.javadsl.FishingOutcomes;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import io.vavr.collection.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.versions.api.models.VersionedCommit;
import org.spongepowered.downloads.versions.worker.actor.CommitResolver;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

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
        final var actor = testKit.spawn(CommitResolver.resolveCommit());
        final var coords = new ArtifactCoordinates("com.example", "test").version("1.0");
        final var commit = "d838fee5d8e834ba9fd4d1c4fe0f8214d6dc90fc";
        final var url = "https://github.com/spongepowered/configurate.git";
        final var uri = List.of(URI.create(url));
        final var probe = testKit
            .<Optional<VersionedCommit>>createTestProbe();
        final var replyTo = probe
            .ref();
        actor.tell(new CommitResolver.ResolveCommitDetails(
            coords, commit, uri, replyTo
        ));

        probe.fishForMessage(Duration.ofSeconds(60), m -> {
            if (m.isEmpty()) {
                return FishingOutcomes.fail("got an empty optional");
            }
            return FishingOutcomes.complete();
        });
    }

    @Test
    public void verifyNonExistentRepo() {
        final var actor = testKit.spawn(CommitResolver.resolveCommit());
        final var coords = new ArtifactCoordinates("com.example", "test").version("1.0");
        final var commit = "d838fee5d8e834ba9fd4d1c4fe0f8214d6dc90fc";
        final var url = "https://example.com/git/doesnt-exist.git";
        final var uri = List.of(URI.create(url));
        final var probe = testKit
            .<Optional<VersionedCommit>>createTestProbe();
        final var replyTo = probe
            .ref();
        actor.tell(new CommitResolver.ResolveCommitDetails(
            coords, commit, uri, replyTo
        ));
        probe.fishForMessage(Duration.ofSeconds(10), m -> {
            if (m.isPresent()) {
                return FishingOutcomes.fail("Got a present optional: " + m.get());
            }
            return FishingOutcomes.complete();
        });
    }


    @Test
    public void verifyNonExistentCommit() {
        final var actor = testKit.spawn(CommitResolver.resolveCommit());
        final var coords = new ArtifactCoordinates("com.example", "test").version("1.0");
        final var commit = "a830fee5d8e894ba9aa4a1a4fe0f8214d6dc90fc";
        final var url = "https://github.com/spongepowered/configurate.git";
        final var uri = List.of(URI.create(url));
        final var probe = testKit
            .<Optional<VersionedCommit>>createTestProbe();
        final var replyTo = probe
            .ref();
        actor.tell(new CommitResolver.ResolveCommitDetails(
            coords, commit, uri, replyTo
        ));
        probe.fishForMessage(Duration.ofSeconds(60), m -> {
            if (m.isPresent()) {
                return FishingOutcomes.fail("got a present optional... " + m.get());
            }
            return FishingOutcomes.complete();
        });
    }
}
