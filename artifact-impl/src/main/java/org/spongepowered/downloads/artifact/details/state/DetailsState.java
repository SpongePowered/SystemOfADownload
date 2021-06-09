package org.spongepowered.downloads.artifact.details.state;

import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

public interface DetailsState {
    ArtifactCoordinates coordinates();
    String displayName();
    String website();
    String gitRepository();
    String issues();
    boolean isEmpty();

}
