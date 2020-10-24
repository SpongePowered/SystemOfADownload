package org.spongepowered.downloads.artifact.api.registration;

public final record RegisterArtifactRequest(
    String artifactId,
    String version
)
{
}
