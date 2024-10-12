package org.spongepowered.downloads.test.artifacts.server.cmd.transport;


import io.micronaut.http.HttpStatus;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.downloads.artifact.api.registration.ArtifactRegistration;
import org.spongepowered.downloads.artifacts.server.cmd.transport.ArtifactCommandController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestResourcesScope("testcontainers")
public class ArtifactControllerTest {
    @Inject ArtifactCommandController controller;

    private static final Logger logger = LoggerFactory.getLogger("ArtifactControllerTest");

    @BeforeAll
    void init() {
        System.out.println("BeforeAll");
    }


    @Test
    void testRequiredArgumentsPostNewArtifact() {
        Assertions.assertThrows(ConstraintViolationException.class, () -> this.controller.registerArtifact(null, null));
    }

    @Test
    void testRequiredPostBodyNewArtifact() {
        Assertions.assertThrows(ConstraintViolationException.class, () -> this.controller.registerArtifact("com.example", null));
    }

    @Test
    void testRequiredBodyNewArtifact() {
        final var response = this.controller.registerArtifact(
            "com.example", new ArtifactRegistration.RegisterArtifact("example", "Example"));

        assertNotNull(response);
        assertEquals(response.status(), HttpStatus.NOT_FOUND);
    }

}
