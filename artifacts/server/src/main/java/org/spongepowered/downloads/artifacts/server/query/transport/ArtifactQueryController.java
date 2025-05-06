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

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Status;
import jakarta.inject.Inject;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.query.GetArtifactDetailsResponse;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifacts.server.query.meta.ArtifactRepository;
import reactor.core.publisher.Mono;

@Controller("/groups/{groupID}/artifacts")
@Requires("query")
public class ArtifactQueryController {

    private final ArtifactRepository artifactsRepo;

    @Inject
    public ArtifactQueryController(
        final ArtifactRepository artifactsRepo
    ) {
        this.artifactsRepo = artifactsRepo;
    }

    @Get(produces = MediaType.APPLICATION_JSON)
    @Status(HttpStatus.OK)
    public Mono<GetArtifactsResponse> getArtifacts(
        final @PathVariable String groupID
    ) {
        return this.artifactsRepo.findArtifactIdByGroupId(groupID)
            .collectList()
            .<GetArtifactsResponse>map(GetArtifactsResponse.ArtifactsAvailable::new)
            .onErrorReturn(new GetArtifactsResponse.GroupMissing(groupID));
    }

    /**
     * Get the details of an artifact.
     *
     * @param groupID    The group ID of the artifact
     * @param artifactId The artifact ID of the artifact
     * @return The details of the artifact
     */
    @Get(
        value = "/{artifactId}",
        produces = MediaType.APPLICATION_JSON
    )
    @Status(HttpStatus.OK)
    public Mono<GetArtifactDetailsResponse> getArtifact(
        final @PathVariable String groupID,
        final @PathVariable String artifactId
    ) {
        return this.artifactsRepo.findByGroupIdAndArtifactId(groupID, artifactId)
            .<GetArtifactDetailsResponse>map(a -> new GetArtifactDetailsResponse.RetrievedArtifact(
                a.coordinates(),
                a.displayName(),
                a.website(),
                a.gitRepo(),
                a.issues(),
                a.tags()
            ))
            .onErrorReturn(new GetArtifactDetailsResponse.MissingArtifact(new ArtifactCoordinates(groupID, artifactId)));
    }
}
