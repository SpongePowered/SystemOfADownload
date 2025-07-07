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
package org.spongepowered.downloads.artifacts.server.cmd.details;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.query.ArtifactDetails;
import org.spongepowered.downloads.artifacts.server.cmd.details.domain.Artifact;
import org.spongepowered.downloads.artifacts.server.cmd.group.GroupRepository;
import org.spongepowered.downloads.artifacts.server.cmd.group.domain.Group;
import org.spongepowered.downloads.events.outbox.OutboxRepository;

import java.util.Optional;

@Singleton
@Introspected
public class ArtifactsService {

    @Inject
    private GroupRepository groupRepository;

    @Inject
    private ArtifactRepository artifactRepository;

    @Inject
    private OutboxRepository outboxRepository;

    @Transactional
    public ArtifactDetails.Response updateArtifactDetails(final ArtifactCoordinates coordinates,
        final DetailsCommand command) {
        final Optional<Group> group = this.groupRepository.findByGroupId(coordinates.groupId());
        if (group.isEmpty()) {
            return new ArtifactDetails.Response.NotFound(String.format("Group %s not found", coordinates.groupId()));
        }
        final Optional<Artifact> artifactOpt = artifactRepository.findByGroupAndArtifactId(group.get(), coordinates.artifactId());
        if (artifactOpt.isEmpty()) {
            return new ArtifactDetails.Response.NotFound(String.format("artifact %s not found", coordinates.artifactId()));
        }

        final var artifact = artifactOpt.get();
        final var resp = artifact.update(command);
        if (resp.isEmpty()) {
            return new ArtifactDetails.Response.NotModified();
        }
        this.artifactRepository.update(resp.updatedObject());
        this.outboxRepository.saveAll(resp.events());
        return new ArtifactDetails.Response.Ok(
            artifact.name(),
            artifact.description(),
            artifact.website(),
            "",
            artifact.gitRepo()
        );
    }

}
