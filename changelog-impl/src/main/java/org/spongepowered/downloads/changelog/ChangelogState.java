package org.spongepowered.downloads.changelog;

import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.git.api.Commit;

import java.net.MalformedURLException;
import java.net.URL;

public class ChangelogState {


    private final String coordinates;
    private final List<Commit> commits; // Can be empty

    public ChangelogState(
        final String mavenCoordinates,
        final List<Commit> commits
    ) {
        this.coordinates = mavenCoordinates;
        this.commits = commits;
    }

    public static ChangelogState empty() {
        return new ChangelogState("", List.empty());
    }

    public String getCoordinates() {
        return this.coordinates;
    }

    public List<Commit> getCommits() {
        return this.commits;
    }
}
