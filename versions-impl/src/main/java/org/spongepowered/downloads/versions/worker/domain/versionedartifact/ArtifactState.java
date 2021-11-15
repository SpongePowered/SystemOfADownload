package org.spongepowered.downloads.versions.worker.domain.versionedartifact;

import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.VersionedCommit;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public sealed interface ArtifactState {

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

    final record FileStatus(
        Optional<String> commitSha,
        Optional<VersionedCommit> commit,
        boolean scanned,
        io.vavr.collection.List<Artifact> artifacts
    ) {
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
        Set<String> repo,
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

        public List<ArtifactEvent> addAssets(VersionedArtifactCommand.AddAssets cmd) {
            final var filtered = cmd.artifacts()
                .filter(Predicate.not(this.fileStatus.artifacts::contains));
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
                    this.fileStatus.scanned,
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
    }
}
