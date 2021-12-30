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
package org.spongepowered.synchronizer.gitmanaged.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.TreeMap;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.VersionedCommit;

import java.net.URI;
import java.util.Comparator;
import java.util.Optional;

@JsonDeserialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
    property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(GitState.Empty.class),
    @JsonSubTypes.Type(GitState.Registered.class)
})
public sealed interface GitState extends Jsonable {

    Comparator<MavenCoordinates> LATEST_COMPARATOR = Comparator.<MavenCoordinates, ComparableVersion>comparing(
            m -> new ComparableVersion(m.version))
        .reversed();

    GitState withRepository(URI repository);

    List<URI> repositories();

    GitCommand.UnresolvedWork unresolvedVersions();

    GitState withResolvedVersion(
        MavenCoordinates coordinates, final VersionedCommit commit
    );

    GitState withRawCommit(MavenCoordinates coordinates, String commitSha);

    GitState withUnresolvedVersion(MavenCoordinates coordinates, final String commit);

    @JsonTypeName("empty")
    final record Empty() implements GitState {
        @JsonCreator
        public Empty {
        }

        @Override
        public GitState withRepository(final URI repository) {
            return new Registered(
                List.of(repository), TreeMap.empty(LATEST_COMPARATOR),
                HashMap.empty()
            );
        }

        @Override
        public List<URI> repositories() {
            return List.empty();
        }

        @Override
        public GitCommand.UnresolvedWork unresolvedVersions() {
            return GitCommand.EMPTY;
        }

        @Override
        public GitState withResolvedVersion(
            final MavenCoordinates coordinates,
            final VersionedCommit commit
        ) {
            return new Registered(
                List.empty(),
                TreeMap.empty(LATEST_COMPARATOR),
                HashMap.of(coordinates, commit)
            );
        }

        @Override
        public GitState withRawCommit(final MavenCoordinates coordinates, final String commitSha) {
            return new Registered(
                List.empty(),
                TreeMap.of(LATEST_COMPARATOR, coordinates, Optional.of(commitSha)),
                HashMap.empty()
            );
        }

        @Override
        public GitState withUnresolvedVersion(
            final MavenCoordinates coordinates,
            final String commit
        ) {
            return new Registered(
                List.empty(),
                TreeMap.of(LATEST_COMPARATOR, coordinates, Optional.of(commit)),
                HashMap.empty(),
                HashMap.of(coordinates, commit)
            );
        }
    }

    @JsonTypeName("registered")
    final record Registered(
        List<URI> repository,
        Map<MavenCoordinates, Optional<String>> commits,
        Map<MavenCoordinates, VersionedCommit> resolved,
        Map<MavenCoordinates, String> unresolvable
    ) implements GitState {
        @JsonCreator
        public Registered {
        }

        public Registered(
            final List<URI> repository,
            final Map<MavenCoordinates, Optional<String>> commits,
            final Map<MavenCoordinates, VersionedCommit> resolved
        ) {
            this(repository, commits, resolved, HashMap.empty());
        }

        @Override
        public GitState withRepository(final URI repository) {
            final var repos = this.repository.toSet().add(repository).toList();
            // Reset unresolved commits by re-merging with the existing set of commits
            final var newCommits = this.commits.merge(
                this.unresolvable.mapValues(Optional::of), (a, b) -> a.isEmpty() ? b : a);
            return new Registered(repos, newCommits, this.resolved, HashMap.empty());
        }

        @Override
        public List<URI> repositories() {
            return this.repository;
        }

        @Override
        public GitCommand.UnresolvedWork unresolvedVersions() {
            if (this.repository.isEmpty()) {
                return GitCommand.EMPTY;
            }
            final var unresolvedCommits = this.commits.filterValues(Optional::isPresent)
                .mapValues(Optional::get);
            // Try to get the latest to work with in the first place
            final var versionsWithCommits = unresolvedCommits
                .toSortedMap(LATEST_COMPARATOR.reversed(), t -> t._1, t -> t._2)
                .take(16);
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
            final var newUnresolved = this.unresolvable.remove(coordinates);
            return new Registered(this.repository, newCommits, newResolved, newUnresolved);
        }

        @Override
        public GitState withRawCommit(final MavenCoordinates coordinates, final String commitSha) {
            if (this.resolved.containsKey(coordinates)) {
                return this;
            }
            final var newCommits = this.commits.put(coordinates, Optional.of(commitSha));
            return new Registered(this.repository, newCommits, this.resolved);
        }

        @Override
        public GitState withUnresolvedVersion(
            final MavenCoordinates coordinates,
            final String commit
        ) {
            if (this.resolved.containsKey(coordinates)) {
                return this;
            }
            if (this.unresolvable.containsKey(coordinates)) {
                return this;
            }
            final var newUnresolvable = this.unresolvable.put(coordinates, commit);
            final var newCommits = this.commits.remove(coordinates);
            return new Registered(
                this.repository,
                newCommits,
                this.resolved,
                newUnresolvable
            );
        }
    }
}
