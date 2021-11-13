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
package org.spongepowered.downloads.versions.worker.domain;

import akka.Done;
import akka.actor.typed.ActorRef;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.VersionedCommit;

import java.net.URI;

public interface GitCommand {

    final record RegisterArtifact(
        ArtifactCoordinates coordinates,
        ActorRef<Done> replyTo
    ) implements GitCommand {}

    final record RegisterRepository(
        String repo,
        ActorRef<Done> replyTo
    ) implements GitCommand {}

    final record RegisterVersion(
        MavenCoordinates coordinates,
        ActorRef<Done> replyTo
    ) implements GitCommand {}

    final record AssociateCommitWithVersion(
        String sha,
        MavenCoordinates coordinates,
        ActorRef<Done> replyTo
    ) implements GitCommand {}

    final record GetGitRepo(ActorRef<RepositoryCommand.Response> replyTo) implements GitCommand {
    }

    final record CheckIfWorkIsNeeded(ArtifactCollection collection, ActorRef<RepositoryCommand.Response> replyTo) implements GitCommand {

    }

    final record GetUnCommittedVersions(ActorRef<List<MavenCoordinates>> reply) implements GitCommand {
    }

    final record NotifyCommitMissingFromAssets(MavenCoordinates coordinates, ActorRef<Done> replyTo)
        implements GitCommand {
    }
    final record AssociateCommitDetailsForVersion(
        MavenCoordinates coordinates,
        VersionedCommit commit,
        URI repo,
        ActorRef<Done> replyTo
    ) implements GitCommand {}
}
