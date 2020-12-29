package org.spongepowered.downloads.changelog.api.query;

import org.spongepowered.downloads.artifact.api.Artifact;

import java.util.Objects;

public final class GenerateChangelogRequest {
    private final Artifact artifact;
    private final String componentId;

    public GenerateChangelogRequest(final Artifact artifact, final String componentId) {
        this.artifact = artifact;
        this.componentId = componentId;
    }

    public Artifact artifact() {
        return this.artifact;
    }

    public String componentId() {
        return this.componentId;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        final var that = (GenerateChangelogRequest) obj;
        return Objects.equals(this.artifact, that.artifact) &&
            Objects.equals(this.componentId, that.componentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.artifact, this.componentId);
    }

    @Override
    public String toString() {
        return "GenerateChangelogRequest[" +
            "artifact=" + this.artifact + ", " +
            "componentId=" + this.componentId + ']';
    }

}
