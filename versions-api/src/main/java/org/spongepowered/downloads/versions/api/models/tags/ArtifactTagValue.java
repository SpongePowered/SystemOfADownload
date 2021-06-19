package org.spongepowered.downloads.versions.api.models.tags;

import io.vavr.collection.Map;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;

public final record ArtifactTagValue(MavenCoordinates coordinates, Map<String, String> tagValues) {
}
