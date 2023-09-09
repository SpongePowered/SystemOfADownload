package org.spongepowered.downloads.artifacts.server.query.meta;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.Set;

/**
 * DTO for {@link org.spongepowered.downloads.artifacts.server.query.meta.domain.JpaArtifact}
 */
public record ArtifactDto(
    @NotNull String groupId,
    @NotEmpty String artifactId,
    String displayName,
    String website,
    String gitRepo,
    String issues,
    @NotNull Set<Tag> tagValues
) implements Serializable {
    /**
     * DTO for {@link org.spongepowered.downloads.artifacts.server.query.meta.domain.JpaArtifactTagValue}
     */
    public record Tag(
        String artifactId,
        String groupId,
        String tagName,
        String tagValue
    ) implements Serializable {
    }
}
