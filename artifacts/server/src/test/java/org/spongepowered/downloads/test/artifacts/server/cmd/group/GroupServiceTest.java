package org.spongepowered.downloads.test.artifacts.server.cmd.group;

import io.micronaut.http.HttpStatus;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.spongepowered.downloads.artifacts.server.cmd.group.GroupCommand;
import org.spongepowered.downloads.artifacts.server.cmd.group.GroupService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest(startApplication = false)
public class GroupServiceTest {

    @Inject
    GroupService service;

    @Test
    void testRegisterGroup() {
        final var response = this.service.registerGroup(new GroupCommand.RegisterGroup("org.spongepowered", "SpongePowered", "https://spongepowered.org"));
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
    }
}
