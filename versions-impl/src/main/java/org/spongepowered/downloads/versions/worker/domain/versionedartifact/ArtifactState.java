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
package org.spongepowered.downloads.versions.worker.domain.versionedartifact;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.HashSet;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.VersionedCommit;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@JsonDeserialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ArtifactState.Unregistered.class, name = "unregistered"),
    @JsonSubTypes.Type(value = ArtifactState.Registered.class, name = "registered")
})
public sealed interface ArtifactState extends Jsonable {

    io.vavr.collection.List<Artifact> artifacts();

    default boolean needsArtifactScan() {
        return false;
    }

    default boolean needsCommitResolution() {
        return false;
    }

    default Optional<String> commitSha() {
        return Optional.empty();
    }

    default io.vavr.collection.List<URI> repositories() {
        return io.vavr.collection.List.empty();
    }

    final record Unregistered() implements ArtifactState {
        private static final Unregistered INSTANCE = new Unregistered();

        public ArtifactEvent register(VersionedArtifactCommand.Register cmd) {
            return new ArtifactEvent.Registered(cmd.coordinates());
        }

        @Override
        public io.vavr.collection.List<Artifact> artifacts() {
            return io.vavr.collection.List.empty();
        }
    }

    @JsonDeserialize
    final record FileStatus(
        Optional<String> commitSha,
        Optional<VersionedCommit> commit,
        boolean scanned,
        io.vavr.collection.List<Artifact> artifacts
    ) {
        @JsonCreator
        public FileStatus {
        }

        static final FileStatus EMPTY = new FileStatus(
            Optional.empty(),
            Optional.empty(),
            true,
            io.vavr.collection.List.empty()
        );

    }

    static Registered register(final ArtifactEvent.Registered event) {
        return new Registered(event.coordinates(), HashSet.empty(), FileStatus.EMPTY);
    }

    final record Registered(
        MavenCoordinates coordinates,
        HashSet<String> repo,
        FileStatus fileStatus
    ) implements ArtifactState {

        @Override
        public Optional<String> commitSha() {
            return this.fileStatus.commitSha;
        }

        @Override
        public boolean needsArtifactScan() {
            return !this.fileStatus.scanned;
        }

        @Override
        public boolean needsCommitResolution() {
            return this.fileStatus.commit.isEmpty() && this.fileStatus.commitSha.isPresent();
        }

        @Override
        public io.vavr.collection.List<URI> repositories() {
            return this.repo.toList().map(URI::create);
        }

        public List<ArtifactEvent> addAssets(VersionedArtifactCommand.AddAssets cmd) {

            final var filtered = cmd.artifacts()
                .filter(Predicate.not(a -> this.fileStatus.artifacts.map(Artifact::downloadUrl).contains(a.downloadUrl())));
            if (filtered.isEmpty()) {
                return List.of();
            }

            return List.of(new ArtifactEvent.AssetsUpdated(filtered));
        }

        public ArtifactState withAssets(io.vavr.collection.List<Artifact> artifacts) {
            return new Registered(
                this.coordinates,
                this.repo,
                new FileStatus(
                    this.fileStatus.commitSha,
                    this.fileStatus.commit,
                    this.fileStatus.commit.isPresent() || this.fileStatus.commitSha.isPresent(),
                    artifacts
                )
            );
        }

        @Override
        public io.vavr.collection.List<Artifact> artifacts() {
            return this.fileStatus.artifacts();
        }

        public ArtifactState markFilesErrored() {
            return new Registered(
                this.coordinates,
                this.repo,
                new FileStatus(
                    this.fileStatus().commitSha,
                    this.fileStatus.commit,
                    true,
                    this.fileStatus.artifacts
                )
            );
        }

        public List<ArtifactEvent> associateCommit(String commitSha) {
            if (this.fileStatus.commitSha.isPresent()) {
                return List.of();
            }
            return List.of(new ArtifactEvent.CommitAssociated(this.coordinates, this.repo.toList(), commitSha));
        }

        public ArtifactState withCommit(String commitSha) {
            return new Registered(
                this.coordinates,
                this.repo,
                new FileStatus(
                    Optional.of(commitSha),
                    this.fileStatus.commit,
                    true,
                    this.fileStatus.artifacts
                )
            );
        }

        public ArtifactState resolveCommit(ArtifactEvent.CommitResolved event) {
            return new Registered(
                this.coordinates,
                this.repo,
                new FileStatus(
                    this.fileStatus.commitSha,
                    Optional.of(event.versionedCommit()),
                    this.fileStatus.scanned,
                    this.fileStatus.artifacts
                )
            );
        }

        public ArtifactState withRepository(String repository) {
            return new Registered(
                this.coordinates,
                this.repo.add(repository),
                this.fileStatus
            );
        }
    }
}
