package org.spongepowered.downloads.test.artifacts.server.cmd.transport;


import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.typed.ActorSystem;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Join;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.registration.ArtifactRegistration;
import org.spongepowered.downloads.artifacts.server.cmd.group.GroupCommand;
import org.spongepowered.downloads.artifacts.server.cmd.group.GroupEntity;
import org.spongepowered.downloads.artifacts.server.cmd.transport.ArtifactCommandController;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestResourcesScope("testcontainers")
public class ArtifactControllerTest {
    @Inject ArtifactCommandController controller;

    @Inject ActorSystem<?> system;

    @Inject ClusterSharding sharding;

    @Inject ActorTestKit testkit;

    private static final Logger logger = LoggerFactory.getLogger("ArtifactControllerTest");

    @BeforeAll
    void init() {
        System.out.println("BeforeAll");
    }

    @BeforeEach
    void initCluster() {
        final var cluster = Cluster.get(this.system);
        cluster.manager().tell(new Join(cluster.selfMember().address()));
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
        final var probe = this.testkit.createTestProbe(GroupRegistration.Response.class);
        this.sharding.init(Entity.of(
            GroupEntity.ENTITY_TYPE_KEY,
            GroupEntity::create
        ));

        this.sharding.entityRefFor(GroupEntity.ENTITY_TYPE_KEY, "com.example").tell(new GroupCommand.RegisterGroup(
            "com.example",
            "example",
            ",",
            probe.ref()
        ));
        final var groupRegistrationResp = probe.expectMessageClass(GroupRegistration.Response.GroupRegistered.class);
        logger.info("{} response", groupRegistrationResp);

        final var future = this.controller.registerArtifact("com.example", new ArtifactRegistration.RegisterArtifact("example", "Example"));
        final var response = future.toCompletableFuture().join();
        Assertions.assertNotNull(response);
    }
}
