package org.spongepowered.downloads.git.api;

public final class RepositoryRegistration {

    public final String name;
    public final String url;
    public final String branch;


    public RepositoryRegistration(final String name, final String url, final String branch) {
        this.name = name;
        this.url = url;
        this.branch = branch;
    }
}
