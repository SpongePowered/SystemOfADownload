package org.spongepowered.downloads.artifact.api;

import io.vavr.collection.Map;

import java.util.StringJoiner;

public final class ArtifactCollection {

    private final Map<String, Artifact> artifactComponents;
    private final Group group;
    private final String artifactId;
    private final String version;
    private final String mavenCoordinates;

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
        this.mavenCoordinates = new StringJoiner(":")
            .add(this.group.getGroupCoordinates())
            .add(this.artifactId)
            .add(this.version)
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

    public String getMavenCoordinates() {
        return this.mavenCoordinates;
    }
}
