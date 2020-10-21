package org.spongepowered.downloads.git.api;

public final class RepositoryRegistration {

    public final String name;
    public final String website;
    public final String gitUrl;


    public RepositoryRegistration(final String name, final String website, final String gitUrl) {
        this.name = name;
        this.website = website;
        this.gitUrl = gitUrl;
    }
}
