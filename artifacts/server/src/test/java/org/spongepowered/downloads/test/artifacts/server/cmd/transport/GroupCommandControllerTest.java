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
package org.spongepowered.downloads.test.artifacts.server.cmd.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifacts.server.cmd.group.GroupCommand;

@MicronautTest()
public class GroupCommandControllerTest {


    @Inject
    @Client("/")
    HttpClient httpClient;



    @Test
    void registerGroupWithValidData() {
        final var client = httpClient.toBlocking();
        final var response = client.exchange(HttpRequest.POST(
            "/groups",
            new GroupCommand.RegisterGroup(
                "org.example.valid",
                "ValidGroup",
                "https://example.com/valid"
            )
        ));

        assertEquals(200, response.getStatus().getCode());
        final var body = response.getBody(GroupRegistration.Response.GroupRegistered.class);
        assertTrue(body.isPresent());
        assertEquals(new GroupRegistration.Response.GroupRegistered(
            new Group(
                "org.example.valid", "ValidGroup", "https://example.com/valid"
            )
        ), body.get());
    }

    @Test
    void registerGroupWithInvalidGroupId() {
        final var client = httpClient.toBlocking();
        assertThrows(HttpClientResponseException.class, () -> client.exchange(HttpRequest.POST(
             "/groups",
             new GroupCommand.RegisterGroup(
                 "invalid group id",
                 "InvalidGroup",
                 "https://example.com/invalid"
             )
         ))
        );
    }

    @Test
    void registerGroupWithEmptyName() {
        final var client = httpClient.toBlocking();
        assertThrows(HttpClientResponseException.class, () -> client.exchange(HttpRequest.POST(
             "/groups",
             new GroupCommand.RegisterGroup(
                 "org.example.empty",
                 "",
                 "https://example.com/empty"
             )
         ))
        );
    }

    @Test
    void registerGroupWithInvalidUrl() {
        final var client = httpClient.toBlocking();
        assertThrows(HttpClientResponseException.class, () -> client.exchange(HttpRequest.POST(
            "/groups",
            new GroupCommand.RegisterGroup(
                "org.example.invalid",
                "InvalidUrlGroup",
                "not-a-valid-url"
            )
        ))
        );
    }

    @Test
    void registerDuplicateGroup() {
        final var client = httpClient.toBlocking();
        final var group = new GroupCommand.RegisterGroup(
            "org.example.duplicate",
            "DuplicateGroup",
            "https://example.com/duplicate"
        );

        client.exchange(HttpRequest.POST("/groups", group));

        assertThrows(HttpClientResponseException.class, () -> client.exchange(HttpRequest.POST("/groups", group)));
    }
}
