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
package org.spongepowered.downloads.artifacts.query.impl;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.NotFound;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaSession;
import org.spongepowered.downloads.artifacts.query.api.ArtifactQueryService;
import org.spongepowered.downloads.artifacts.query.api.GetArtifactDetailsResponse;
import org.spongepowered.downloads.artifacts.query.impl.model.JpaArtifact;

import javax.inject.Inject;
import java.util.Locale;

public final record ArtifactQueryServiceImpl(JpaSession session) implements ArtifactQueryService {

    @Inject
    public ArtifactQueryServiceImpl {

    }

    @Override
    public ServiceCall<NotUsed, GetArtifactDetailsResponse> getArtifactDetails(
        final String groupId,
        final String artifactId
    ) {
        return notUsed -> {
            final var sanitizedGroupId = groupId.toLowerCase(Locale.ROOT).trim();
            final var sanitizedArtifactId = artifactId.toLowerCase(Locale.ROOT).trim();
            return this.session.withTransaction(em ->
                em.createNamedQuery("Artifact.findByCoordinates", JpaArtifact.class)
                    .setParameter("groupId", sanitizedGroupId)
                    .setParameter("artifactId", sanitizedArtifactId)
                    .getResultList()
                    .stream()
                    .findFirst()
                    .map(artifact -> {
                        final var coordinates = artifact.getCoordinates();
                        final var tags = artifact.getTagValuesForReply();
                        return new GetArtifactDetailsResponse.RetrievedArtifact(
                            coordinates, artifact.getDisplayName(),
                            null,
                            null,
                            null,
                            tags
                        );
                    })
                    .orElseThrow(() -> new NotFound("group or artifact not found")));
        };
    }
}
