package org.spongepowered.downloads.artifact.details.state;

import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

public final class EmptyState implements DetailsState {
    private static final ArtifactCoordinates empty = new ArtifactCoordinates("", "");
    @Override
    public ArtifactCoordinates coordinates() {
        return empty;
    }

    @Override
    public String displayName() {
        return "";
    }

    @Override
    public String website() {
        return "";
    }

    @Override
    public String gitRepository() {
        return "";
    }

    @Override
    public String issues() {
        return "";
    }

    @Override
    public boolean isEmpty() {
        return true;
    }
}
