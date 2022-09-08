package org.spongepowered.downloads.versions.models;

import java.io.Serializable;

public record VersionedAssetID(
    String groupId,
    String artifactId,
    String version,
    String classifier,
    String extension
    ) implements Serializable {
}
