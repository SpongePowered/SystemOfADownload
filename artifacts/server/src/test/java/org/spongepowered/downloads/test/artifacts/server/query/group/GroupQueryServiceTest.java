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
package org.spongepowered.downloads.test.artifacts.server.query.group;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.artifact.api.query.GroupsResponse;
import org.spongepowered.downloads.artifacts.server.cmd.group.GroupCommand;
import org.spongepowered.downloads.artifacts.server.cmd.group.GroupService;
import org.spongepowered.downloads.artifacts.server.query.group.GroupQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(environments = "test", startApplication = false)
public class GroupQueryServiceTest {

    @Inject
    GroupService groupService;

    @Inject
    GroupQueryService groupQueryService;

    @BeforeEach
    void setup() {
        // Register a test group
        final var request = new GroupCommand.RegisterGroup(
            "org.spongepowered", "SpongePowered", "https://spongepowered.org");
        groupService.registerGroup(request);
    }

    @Test
    void testGetGroups() {
        // Get all groups
        GroupsResponse response = groupQueryService.getGroups();
        
        // Verify response
        assertNotNull(response);
        assertInstanceOf(GroupsResponse.Available.class, response);
        
        GroupsResponse.Available availableResponse = (GroupsResponse.Available) response;
        assertNotNull(availableResponse.groups());
        assertFalse(availableResponse.groups().isEmpty());
        
        // Verify at least one group has the expected data
        boolean foundTestGroup = availableResponse.groups().stream()
            .anyMatch(group -> 
                "org.spongepowered".equals(group.groupCoordinates()) && 
                "SpongePowered".equals(group.name()) && 
                "https://spongepowered.org".equals(group.website())
            );
        
        assertTrue(foundTestGroup, "Test group not found in response");
    }

    @Test
    void testGetGroupDetails() {
        // Get details for existing group
        GroupResponse response = groupQueryService.getGroupDetails("org.spongepowered");
        
        // Verify response
        assertNotNull(response);
        assertInstanceOf(GroupResponse.Available.class, response);
        
        GroupResponse.Available availableResponse = (GroupResponse.Available) response;
        assertEquals("org.spongepowered", availableResponse.group().groupCoordinates());
        assertEquals("SpongePowered", availableResponse.group().name());
        assertEquals("https://spongepowered.org", availableResponse.group().website());
    }

    @Test
    void testGetGroupDetailsForNonExistentGroup() {
        // Get details for non-existent group
        GroupResponse response = groupQueryService.getGroupDetails("org.nonexistent");
        
        // Verify response
        assertNotNull(response);
        assertInstanceOf(GroupResponse.Missing.class, response);
        
        GroupResponse.Missing missingResponse = (GroupResponse.Missing) response;
        assertEquals("org.nonexistent", missingResponse.groupId());
    }
}
