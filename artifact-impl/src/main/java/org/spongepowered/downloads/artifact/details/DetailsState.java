package org.spongepowered.downloads.artifact.details;

import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

public final class DetailsState {

    public final ArtifactCoordinates coordinates;
    public final String displayName;
    public final String website;
    public final String gitRepository;
    public final String issues;

    public DetailsState() {
        this.coordinates = new ArtifactCoordinates("", "");
        this.displayName = "";
        this.website = "";
        this.gitRepository = "";
        this.issues = "";
    }




}
