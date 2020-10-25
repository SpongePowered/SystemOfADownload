package org.spongepowered.downloads.changelog;

import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.git.api.Commit;

import java.net.MalformedURLException;
import java.net.URL;

public class ChangelogState {

    private static final URL DUMMY;

    static {
        try {
            DUMMY = new URL("http://example.com/");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    private final Artifact artifact;
    private final String coordinates;
    private final List<Commit> commits; // Can be empty

    public ChangelogState(
        final Artifact artifact,
        final List<Commit> commits
    ) {
        this.artifact = artifact;
        this.coordinates = artifact.getFormattedString(":");
        this.commits = commits;
    }

    public static ChangelogState empty() {
        return new ChangelogState(new Artifact(new Group("empty", "empty", new URL("http://example.com"))), List.empty());
    }

    public Artifact getArtifact() {
        return this.artifact;
    }

    public String getCoordinates() {
        return this.coordinates;
    }

    public List<Commit> getCommits() {
        return this.commits;
    }
}
