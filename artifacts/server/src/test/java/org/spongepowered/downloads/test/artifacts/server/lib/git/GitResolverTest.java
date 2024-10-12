package org.spongepowered.downloads.test.artifacts.server.lib.git;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.spongepowered.downloads.artifact.api.query.ArtifactDetails;
import org.spongepowered.downloads.artifacts.server.lib.git.GitResolver;

@MicronautTest(startApplication = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GitResolverTest {

    @Inject GitResolver resolver;

    @Test
    void testResolveInvalidURL() {
        final var either = resolver.validateRepository("not://a/valid/url");
        final var join = either.join();
        Assertions.assertInstanceOf(ArtifactDetails.Response.Error.class, join);
    }

    @Test
    void testResolveValidURL() {
        final var either = resolver.validateRepository("https://github.com/SpongePowered/SpongeAPI.git");
        final var join = either.join();
        Assertions.assertInstanceOf(ArtifactDetails.Response.ValidRepo.class, join);
    }

    @Test
    void testResolveUnsupportedTransport() {
        final var either = resolver.validateRepository("git://github.com/SpongePowered/SpongeAPI.git");
        final var join = either.join();
        Assertions.assertInstanceOf(ArtifactDetails.Response.Error.class, join);
    }
}
