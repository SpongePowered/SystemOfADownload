package org.spongepowered.downloads.artifact.api;

import io.swagger.v3.oas.annotations.media.Schema;
import io.vavr.collection.Map;

import java.util.StringJoiner;

public final class ArtifactCollection {

    @Schema(required = false, description = "A map of artifact kind to artifact information")
    private final Map<String, Artifact> artifactComponents;
    @Schema(
        required = true,
        description = "The group for an artifact collection"
    )
    private final Group group;
    @Schema(required = true)
    private final String artifactId;
    @Schema(required = true)
    private final String version;
    private final String mavenCoordinates;
    @Schema(required = true)
    private final String mavenVersion;

    public ArtifactCollection(
        final Map<String, Artifact> artifactComponents,
        final Group group,
        final String artifactId,
        final String version
    ) {
        this.artifactComponents = artifactComponents;
        this.group = group;
        this.artifactId = artifactId;
        this.version = version;
        this.mavenVersion = version;
        this.mavenCoordinates = new StringJoiner(":")
            .add(this.group.getGroupCoordinates())
            .add(this.artifactId)
            .add(this.version)
            .toString();
    }

    public ArtifactCollection(
        final Map<String, Artifact> artifactComponents,
        final Group group,
        final String artifactId,
        final String version,
        final String mavenVersion
    ) {
        this.artifactComponents = artifactComponents;
        this.group = group;
        this.artifactId = artifactId;
        this.version = version;
        this.mavenVersion = mavenVersion;
        this.mavenCoordinates = new StringJoiner(":")
            .add(this.group.getGroupCoordinates())
            .add(this.artifactId)
            .add(this.mavenVersion)
            .toString();
    }

    public Map<String, Artifact> getArtifactComponents() {
        return this.artifactComponents;
    }

    public Group getGroup() {
        return this.group;
    }

    public String getArtifactId() {
        return this.artifactId;
    }

    public String getVersion() {
        return this.version;
    }

    public String getMavenVersion() {
        return this.mavenVersion;
    }

    public String getMavenCoordinates() {
        return this.mavenCoordinates;
    }
}
