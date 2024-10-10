package org.spongepowered.downloads.test.artifacts.server;

import io.micronaut.context.BeanContext;
import io.micronaut.data.annotation.Query;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifacts.server.query.meta.ArtifactRepository;
import org.spongepowered.downloads.artifacts.server.query.meta.domain.JpaArtifact;
import reactor.core.publisher.Mono;

import java.util.Collections;


@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestResourcesScope("testcontainers")
public class ArtifactRepositoryTest {
    @Inject
    BeanContext context;

    @Inject
    EmbeddedApplication<?> application;


    private final Logger logger = LoggerFactory.getLogger("ArtifactRepositoryTest");

    @Test
    public void testItWorks() {
        Assertions.assertTrue(application.isRunning());
    }

    @Test
    public void testAnnotation() {
        final BeanDefinition<ArtifactRepository> beanDefinition = context.getBeanDefinition(ArtifactRepository.class);
        final ExecutableMethod<ArtifactRepository, Object> findByGroupIdAndArtifactId = beanDefinition // (1)
            .getRequiredMethod("findByGroupIdAndArtifactId", String.class, String.class);
        String query = findByGroupIdAndArtifactId // (2)
            .getAnnotationMetadata().stringValue(Query.class) // (3)
            .orElse(null);

        final String expected = "SELECT artifact.\"id\",artifact.\"group_id\",artifact.\"artifact_id\",artifact.\"display_name\",artifact.\"website\",artifact.\"git_repository\",artifact.\"issues\" FROM \"artifact\".\"artifacts\" artifact WHERE (artifact.\"group_id\" = $1 AND artifact.\"artifact_id\" = $2)";
        Assertions.assertEquals( // (4)
            expected, query);
    }

    @Test
    public void testGetArtifact() {
        final ArtifactRepository repo = context.createBean(ArtifactRepository.class);

        // Example is injected with test data through liquibase resources
        final Mono<JpaArtifact> spongevanilla = repo.findByGroupIdAndArtifactId("com.example", "example");
        final JpaArtifact a = spongevanilla.block();


        final var expected = new ArtifactCoordinates("com.example", "example");
        Assertions.assertNotNull(a);
        Assertions.assertEquals(expected, a.coordinates());
        final var expectedTags = Collections.emptyMap();
        Assertions.assertNotNull(a.tags());
        Assertions.assertEquals(expectedTags, a.tags());
    }



}
