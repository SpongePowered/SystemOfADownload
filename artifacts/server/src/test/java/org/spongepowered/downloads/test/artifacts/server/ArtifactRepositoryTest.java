/*
 * This file is part of SystemOfADownload, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://spongepowered.org/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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

import java.util.Collections;
import java.util.Optional;


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

        final String expected =
            """
            SELECT artifact."id",
            artifact."group_id",
            artifact."group_internal_id",
            artifact."artifact_id",
            artifact."display_name",
            artifact."website",
            artifact."git_repository",
            artifact."issues" FROM "artifact"."grouped_artifacts" artifact WHERE (artifact."group_id" = ? AND artifact."artifact_id" = ?)
            """.replace("\n", "")
                .replace("            ", "");
        Assertions.assertEquals( // (4)
            expected, query);
    }

    @Test
    public void testGetArtifact() {
        final ArtifactRepository repo = context.createBean(ArtifactRepository.class);

        // Example is injected with test data through liquibase resources
        final Optional<JpaArtifact> spongevanilla = repo.findByGroupIdAndArtifactId("com.example", "example");
        final JpaArtifact a = spongevanilla.orElse(null);


        final var expected = new ArtifactCoordinates("com.example", "example");
        Assertions.assertNotNull(a);
        Assertions.assertEquals(expected, a.coordinates());
        final var expectedTags = Collections.emptyMap();
        Assertions.assertNotNull(a.tags());
        Assertions.assertEquals(expectedTags, a.tags());
    }


}
