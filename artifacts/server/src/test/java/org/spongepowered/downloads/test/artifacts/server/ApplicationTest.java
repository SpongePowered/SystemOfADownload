package org.spongepowered.downloads.test.artifacts.server;

import akka.actor.typed.ActorSystem;
import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@MicronautTest
public class ApplicationTest {


    @Inject
    EmbeddedApplication<?> application;
    @Inject
    ActorSystem<?> system;
    private final Logger logger = LoggerFactory.getLogger("ArtifactRepositoryTest");

    @Test
    public void testItWorks() {
        Assertions.assertTrue(application.isRunning());
        Assertions.assertNotNull(system);
        final String msg = system.printTree();
        logger.info(msg);
    }
}
