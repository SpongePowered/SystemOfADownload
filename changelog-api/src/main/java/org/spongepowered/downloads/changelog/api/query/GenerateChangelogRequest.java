package org.spongepowered.downloads.changelog.api.query;

import org.spongepowered.downloads.artifact.api.Artifact;

public final record GenerateChangelogRequest(Artifact artifact, String componentId) {
}
