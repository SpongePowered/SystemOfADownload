package org.spongepowered.downloads.artifact.api.query;

public final record RegisterArtifactRequest(
    String artifactId,
    String version
)
{
}
