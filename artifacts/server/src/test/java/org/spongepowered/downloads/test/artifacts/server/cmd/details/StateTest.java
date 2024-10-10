package org.spongepowered.downloads.test.artifacts.server.cmd.details;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifacts.events.DetailsEvent;
import org.spongepowered.downloads.artifacts.events.DetailsEvent.ArtifactGitRepositoryUpdated;
import org.spongepowered.downloads.artifacts.server.cmd.details.state.EmptyState;
import org.spongepowered.downloads.artifacts.server.cmd.details.state.PopulatedState;

public class StateTest {

    public static final String GIT_REPO = "https://github.com/SpongePowered/Example.git";
    public static final String EXPECTED_COORDINATES = "org.spongepowered:example";
    public static final String ISSUES_URL = "https://github.com/SpongePowered/Example/issues";

    @Test
    public void testEmptyState() {
        final var state = new EmptyState();
        Assertions.assertTrue(state.isEmpty());
        Assertions.assertEquals(state.coordinates().asMavenString(), ":");
        Assertions.assertEquals(state.gitRepository(), "");
        Assertions.assertEquals(state.displayName(), "");
        Assertions.assertEquals(state.issues(), "");
        Assertions.assertEquals(state.website(), "");
    }

    @Test
    public void testEmptyToPopulated() {
        final var coordinates = new ArtifactCoordinates("org.spongepowered", "example");
        final var populated = new PopulatedState(coordinates, "Example", "", "", "");
        Assertions.assertFalse(populated.isEmpty());
        Assertions.assertEquals(populated.displayName(), "Example");
        Assertions.assertEquals(populated.coordinates().asMavenString(), EXPECTED_COORDINATES);
        Assertions.assertEquals(populated.gitRepository(), "");
        Assertions.assertEquals(populated.issues(), "");
        Assertions.assertEquals(populated.website(), "");
    }

    @Test
    public void testPopulatedWithGit() {
        final var coordinates = new ArtifactCoordinates("org.spongepowered", "example");
        final var populated = new PopulatedState(coordinates, "Example", "", "", "");
        final var git = populated.withGitRepo(new ArtifactGitRepositoryUpdated(coordinates, GIT_REPO));
        Assertions.assertFalse(git.isEmpty());
        Assertions.assertEquals(git.displayName(), "Example");
        Assertions.assertEquals(git.coordinates().asMavenString(), EXPECTED_COORDINATES);
        Assertions.assertEquals(git.gitRepository(), GIT_REPO);
        Assertions.assertEquals(git.issues(), "");
        Assertions.assertEquals(git.website(), "");
    }

    @Test
    public void testPopulatedWithGitAndIssues() {
        final var coordinates = new ArtifactCoordinates("org.spongepowered", "example");
        final var populated = new PopulatedState(coordinates, "Example", "", "", "");
        final var git = populated.withGitRepo(new ArtifactGitRepositoryUpdated(coordinates, GIT_REPO));
        Assertions.assertInstanceOf(PopulatedState.class, git);
        final var issues = ((PopulatedState) git).
            withIssues(new DetailsEvent.ArtifactIssuesUpdated(coordinates, ISSUES_URL));
        Assertions.assertFalse(issues.isEmpty());
        Assertions.assertEquals(issues.displayName(), "Example");
        Assertions.assertEquals(issues.coordinates().asMavenString(), EXPECTED_COORDINATES);
        Assertions.assertEquals(issues.gitRepository(), GIT_REPO);
        Assertions.assertEquals(issues.issues(), ISSUES_URL);
        Assertions.assertEquals(issues.website(), "");
    }

    @Test
    public void testPopulatedWithGitAndIssuesAndWebsite() {
        final var coordinates = new ArtifactCoordinates("org.spongepowered", "example");
        final var populated = new PopulatedState(coordinates, "Example", "", "", "");
        final var git = populated.withGitRepo(new ArtifactGitRepositoryUpdated(coordinates, GIT_REPO));
        Assertions.assertInstanceOf(PopulatedState.class, git);
        final var issues = ((PopulatedState) git).
            withIssues(new DetailsEvent.ArtifactIssuesUpdated(coordinates, ISSUES_URL));
        Assertions.assertInstanceOf(PopulatedState.class, issues);
        final var website = ((PopulatedState) issues).
            withWebsite(new DetailsEvent.ArtifactWebsiteUpdated(coordinates, "https://example.com"));
        Assertions.assertFalse(website.isEmpty());
        Assertions.assertEquals(website.displayName(), "Example");
        Assertions.assertEquals(website.coordinates().asMavenString(), EXPECTED_COORDINATES);
        Assertions.assertEquals(website.gitRepository(), GIT_REPO);
        Assertions.assertEquals(website.issues(), ISSUES_URL);
        Assertions.assertEquals(website.website(), "https://example.com");
    }

    @Test
    public void testPopulatedWithNewDisplayName() {
        final var coordinates = new ArtifactCoordinates("org.spongepowered", "example");
        final var populated = new PopulatedState(coordinates, "Example", "", "", "");
        final var git = populated.withGitRepo(new ArtifactGitRepositoryUpdated(coordinates, GIT_REPO));
        Assertions.assertInstanceOf(PopulatedState.class, git);
        final var issues = ((PopulatedState) git).
            withIssues(new DetailsEvent.ArtifactIssuesUpdated(coordinates, ISSUES_URL));
        Assertions.assertInstanceOf(PopulatedState.class, issues);
        final var website = ((PopulatedState) issues).
            withWebsite(new DetailsEvent.ArtifactWebsiteUpdated(coordinates, "https://example.com"));
        Assertions.assertInstanceOf(PopulatedState.class, website);
        final var displayName = ((PopulatedState) website).
            withDisplayName(new DetailsEvent.ArtifactDetailsUpdated(coordinates, "New Example"));
        Assertions.assertFalse(displayName.isEmpty());
        Assertions.assertEquals(displayName.displayName(), "New Example");
        Assertions.assertEquals(displayName.coordinates().asMavenString(), EXPECTED_COORDINATES);
        Assertions.assertEquals(displayName.gitRepository(), GIT_REPO);
        Assertions.assertEquals(displayName.issues(), ISSUES_URL);
        Assertions.assertEquals(displayName.website(), "https://example.com");
    }
}
