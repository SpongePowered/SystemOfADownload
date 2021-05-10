package org.spongepowered.downloads.versions.sonatype.global;

import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

public final class ManagedArtifacts {

    public final List<ArtifactCoordinates> artifacts;

    public ManagedArtifacts(final List<ArtifactCoordinates> artifacts) {
        this.artifacts = artifacts;
    }

    public ManagedArtifacts withArtifact(final ArtifactCoordinates coordinates) {
        if (!this.artifacts.contains(coordinates)) {
            return new ManagedArtifacts(this.artifacts.append(coordinates));
        }
        return this;
    }

}
