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
package org.spongepowered.downloads.artifacts.server.cmd.group;

import io.micronaut.http.HttpResponse;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.Valid;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.mutation.Update;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.artifact.api.registration.Response;
import org.spongepowered.downloads.artifacts.events.ArtifactEvent;
import org.spongepowered.downloads.artifacts.events.GroupUpdate;
import org.spongepowered.downloads.artifacts.server.cmd.group.domain.Artifact;
import org.spongepowered.downloads.artifacts.server.cmd.group.domain.Group;
import org.spongepowered.downloads.events.outbox.OutboxEvent;
import org.spongepowered.downloads.events.outbox.OutboxRepository;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Singleton
public class GroupService {

    @Inject
    private GroupRepository groupRepository;

    @Inject
    private ArtifactRepository artifactRepository;

    @Inject
    private OutboxRepository outboxRepository;

    @Transactional
    public HttpResponse<GroupRegistration.Response> registerGroup(GroupCommand.RegisterGroup rg) {
        Optional<Group> existingGroup = groupRepository.findByGroupId(rg.mavenCoordinates());
        if (existingGroup.isPresent()) {
            return HttpResponse.badRequest(new GroupRegistration.Response.GroupAlreadyRegistered(rg.mavenCoordinates()));
        }

        Group group = new Group();
        group.setGroupId(rg.mavenCoordinates());
        group.setName(rg.name());
        group.setWebsite(rg.website());
        groupRepository.save(group);

        OutboxEvent event = new OutboxEvent("GroupCreated", group.groupId(), new GroupUpdate.GroupRegistered(rg.mavenCoordinates(), rg.name(), rg.website()));
        Mono.from(outboxRepository.save(event)).then().block();
        final var groupDTO = new org.spongepowered.downloads.artifact.api.Group(group.groupId(), group.name(), group.website());

        return HttpResponse.ok(new GroupRegistration.Response.GroupRegistered(groupDTO));
    }

    @Transactional
    public HttpResponse<Response> registerArtifact(final String groupId, final GroupCommand.RegisterArtifact ra) {
        Optional<Group> groupOptional = groupRepository.findByGroupId(groupId);
        if (groupOptional.isEmpty()) {
            return HttpResponse.notFound(new Response.GroupMissing(groupId));
        }

        Group group = groupOptional.get();
        boolean artifactExists = artifactRepository.existsByArtifactIdAndGroup(ra.artifact(), group);
        if (artifactExists) {
            return HttpResponse.badRequest(new Response.ArtifactRegistered(new ArtifactCoordinates(groupId, ra.artifact())));
        }

        Artifact artifact = new Artifact();
        artifact.setArtifactId(ra.artifact());
        artifact.setGroup(group);
        artifactRepository.save(artifact);

        var event = new OutboxEvent("ArtifactsGroupUpserted", group.groupId(), new GroupUpdate.ArtifactRegistered(artifact.coordinates()));
        Mono.from(outboxRepository.save(event)).then().block();
        event = new OutboxEvent("ArtifactsArtifactUpserted", artifact.getArtifactId(), new ArtifactEvent.ArtifactRegistered(artifact.coordinates()));
        Mono.from(outboxRepository.save(event)).then().block();


        return HttpResponse.ok(new Response.ArtifactRegistered(artifact.coordinates()));
    }

    public HttpResponse<GroupResponse> updateGroup(String groupId, @Valid Update groupUpdateDTO) {

        return null;
    }
}
