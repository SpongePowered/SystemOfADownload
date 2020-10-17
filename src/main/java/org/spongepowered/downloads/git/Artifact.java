package org.spongepowered.downloads.git;

import io.vavr.collection.List;

import java.time.ZonedDateTime;

public final record Artifact(
    String artifactId,
    List<Commit> commits
) {

}
