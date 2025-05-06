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
package org.spongepowered.downloads.artifacts.server.query.transport;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import jakarta.inject.Inject;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.artifact.api.query.GroupsResponse;
import org.spongepowered.downloads.artifacts.server.query.group.GroupQueryService;

@Controller("/groups")
public class GroupQueryController {

    @Inject
    private GroupQueryService groupQueryService;

    @Get
    public HttpResponse<GroupsResponse> getGroups() {
        return HttpResponse.ok(groupQueryService.getGroups());
    }

    @Get("/{groupId}")
    public HttpResponse<GroupResponse> getGroupDetails(@PathVariable String groupId) {
        final var groupDetails = groupQueryService.getGroupDetails(groupId);
        return switch (groupDetails) {
            case GroupResponse.Available a -> HttpResponse.ok(a);
            case GroupResponse.Missing n -> HttpResponse.notFound(n);
        };
    }
}
