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
package org.spongepowered.downloads.changelog;

import akka.NotUsed;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.persistence.ReadSide;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.changelog.api.ChangelogService;
import org.spongepowered.downloads.changelog.api.query.ChangelogResponse;
import org.spongepowered.downloads.git.api.CommitService;

import java.util.StringJoiner;

public class ChangelogServiceImpl implements ChangelogService {

    private final PersistentEntityRegistry registry;

    @Inject
    public ChangelogServiceImpl(
        final PersistentEntityRegistry registry,
        final ReadSide readSide
    ) {
        this.registry = registry;
        readSide.register(ArtifactReadSideProcessor.class);
    }

    @Override
    public ServiceCall<NotUsed, ChangelogResponse> getChangelog(
        final String groupId, final String artifactId, final String version
    ) {
        return notUsed ->
            this.getChangelogEntity(new StringJoiner(":").add(groupId).add(artifactId).add(version).toString())
                .ask(new ChangelogCommand.GetChangelogFromCoordinates(groupId, artifactId, version));

    }

    @Override
    public ServiceCall<NotUsed, NotUsed> registerArtifact(final Artifact artifact) {
        return notUsed -> this.getChangelogEntity(artifact.getFormattedString(":"))
            .ask(new ChangelogCommand.RegisterArtifact(artifact));
    }

    private PersistentEntityRef<ChangelogCommand> getChangelogEntity(final String mavenCoordinates) {
        return this.registry.refFor(ChangelogEntity.class, mavenCoordinates);
    }


}
