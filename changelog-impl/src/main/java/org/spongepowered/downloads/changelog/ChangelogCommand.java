package org.spongepowered.downloads.changelog;

import akka.NotUsed;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.changelog.api.query.ChangelogResponse;

public sealed interface ChangelogCommand {

    @JsonDeserialize
    final record RegisterArtifact(Artifact artifact)
        implements ChangelogCommand, PersistentEntity.ReplyType<NotUsed> {}

    @JsonDeserialize
    final record GetChangelogFromCoordinates(String groupId, String artifactId, String version)
        implements ChangelogCommand, PersistentEntity.ReplyType<ChangelogResponse> {}

    @JsonDeserialize
    final record GetChangelog(Artifact artifact)
        implements ChangelogCommand, PersistentEntity.ReplyType<ChangelogResponse> {}
}
