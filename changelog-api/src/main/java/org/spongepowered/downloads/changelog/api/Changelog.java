package org.spongepowered.downloads.changelog.api;

import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.git.api.Commit;

public record Changelog(Artifact artifact, List<Commit> commits) { }
