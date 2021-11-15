package org.spongepowered.downloads.versions.worker.actor.artifacts;

import org.spongepowered.downloads.artifact.api.MavenCoordinates;

import java.net.URI;

public record PotentiallyUsableAsset(
    MavenCoordinates mavenCoordinates,
    String coordinates,
    URI downloadURL
) {
}
