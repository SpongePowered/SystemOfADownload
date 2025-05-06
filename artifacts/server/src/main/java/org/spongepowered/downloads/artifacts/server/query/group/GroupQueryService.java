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
package org.spongepowered.downloads.artifacts.server.query.group;

import io.micronaut.transaction.annotation.ReadOnly;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.artifact.api.query.GroupsResponse;
import org.spongepowered.downloads.artifacts.server.cmd.group.GroupRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Singleton
public class GroupQueryService {

    @Inject
    private GroupRepository groupRepository;

    @ReadOnly
    public GroupsResponse getGroups() {
        List<Group> groups = StreamSupport.stream(groupRepository.findAll().spliterator(), false)
            .map(this::mapToApiGroup)
            .collect(Collectors.toList());

        return new GroupsResponse.Available(groups);
    }

    @ReadOnly
    public GroupResponse getGroupDetails(String groupId) {
        Optional<org.spongepowered.downloads.artifacts.server.cmd.group.domain.Group> groupOptional = 
            groupRepository.findByGroupId(groupId);

        if (groupOptional.isEmpty()) {
            return new GroupResponse.Missing(groupId);
        }

        org.spongepowered.downloads.artifacts.server.cmd.group.domain.Group domainGroup = groupOptional.get();
        Group apiGroup = mapToApiGroup(domainGroup);

        return new GroupResponse.Available(apiGroup);
    }

    private Group mapToApiGroup(org.spongepowered.downloads.artifacts.server.cmd.group.domain.Group domainGroup) {
        return new Group(
            domainGroup.getGroupId(),
            domainGroup.getName(),
            domainGroup.getWebsite()
        );
    }
}
