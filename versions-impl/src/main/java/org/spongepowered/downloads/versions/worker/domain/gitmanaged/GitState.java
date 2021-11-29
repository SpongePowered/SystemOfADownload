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
package org.spongepowered.downloads.versions.worker.domain.gitmanaged;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.VersionedCommit;

import java.net.URI;
import java.util.Optional;

@JsonDeserialize
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
    @JsonSubTypes.Type(GitState.Empty.class),
    @JsonSubTypes.Type(GitState.Registered.class)
})
public sealed interface GitState extends Jsonable {

    GitState withVersion(MavenCoordinates coordinates);

    GitState withRepository(URI repository);

    List<URI> repositories();

    GitCommand.UnresolvedWork unresolvedVersions();

    GitState withResolvedVersion(
        MavenCoordinates coordinates, final VersionedCommit commit
    );

    GitState withRawCommit(MavenCoordinates coordinates, String commitSha);

    final record Empty() implements GitState {

        @Override
        public GitState withVersion(final MavenCoordinates coordinates) {
            return new Registered(List.empty(), HashMap.of(coordinates, Optional.empty()), HashMap.empty());
        }

        @Override
        public GitState withRepository(final URI repository) {
            return new Registered(List.of(repository), HashMap.empty(), HashMap.empty());
        }

        @Override
        public List<URI> repositories() {
            return List.empty();
        }

        @Override
        public GitCommand.UnresolvedWork unresolvedVersions() {
            return new GitCommand.UnresolvedWork(HashMap.empty(), List.empty());
        }

        @Override
        public GitState withResolvedVersion(
            final MavenCoordinates coordinates,
            final VersionedCommit commit
        ) {
            return new Registered(List.empty(), HashMap.empty(), HashMap.of(coordinates, commit));
        }

        @Override
        public GitState withRawCommit(final MavenCoordinates coordinates, final String commitSha) {
            return new Registered(List.empty(), HashMap.of(coordinates, Optional.of(commitSha)), HashMap.empty());
        }
    }

    final record Registered(
        List<URI> repository,
        Map<MavenCoordinates, Optional<String>> commits,
        Map<MavenCoordinates, VersionedCommit> resolved
    ) implements GitState {

        @Override
        public GitState withVersion(final MavenCoordinates coordinates) {
            if (this.resolved.containsKey(coordinates)) {
                return this;
            }
            return new Registered(this.repository, this.commits.put(coordinates, Optional.empty()), this.resolved);
        }

        @Override
        public GitState withRepository(final URI repository) {
            if (this.repository.contains(repository)) {
                return this;
            }
            return new Registered(this.repository.append(repository), this.commits, this.resolved);
        }

        @Override
        public List<URI> repositories() {
            return this.repository;
        }

        @Override
        public GitCommand.UnresolvedWork unresolvedVersions() {
            final var versionsWithCommits = this.commits.filterValues(Optional::isPresent).mapValues(Optional::get);
            return new GitCommand.UnresolvedWork(versionsWithCommits, this.repository);
        }

        @Override
        public GitState withResolvedVersion(
            final MavenCoordinates coordinates,
            final VersionedCommit commit
        ) {
            if (this.resolved.containsKey(coordinates)) {
                return this;
            }
            final var newCommits = this.commits.remove(coordinates);
            final var newResolved = this.resolved.put(coordinates, commit);
            return new Registered(this.repository, newCommits, newResolved);
        }

        @Override
        public GitState withRawCommit(final MavenCoordinates coordinates, final String commitSha) {
            if (this.resolved.containsKey(coordinates)) {
                return this;
            }
            final var newCommits = this.commits.put(coordinates, Optional.of(commitSha));
            return new Registered(this.repository, newCommits, this.resolved);
        }
    }
}
