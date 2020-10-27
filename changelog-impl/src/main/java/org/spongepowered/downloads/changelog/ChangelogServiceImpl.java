package org.spongepowered.downloads.changelog;

import akka.NotUsed;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.persistence.ReadSide;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.changelog.api.ChangelogService;
import org.spongepowered.downloads.changelog.api.query.ChangelogResponse;
import org.spongepowered.downloads.git.api.CommitService;

import java.util.StringJoiner;

public class ChangelogServiceImpl implements ChangelogService {

    private final PersistentEntityRegistry registry;
    private final ArtifactService artifactService;
    private final CommitService commitService;

    @Inject
    public ChangelogServiceImpl(
        final PersistentEntityRegistry registry,
        final ArtifactService artifactService,
        final CommitService commitService,
        final ReadSide readSide
    ) {
        this.registry = registry;
        this.artifactService = artifactService;
        this.commitService = commitService;
        readSide.register(ArtifactReadSideProcessor.class);
    }

    @Override
    public ServiceCall<NotUsed, ChangelogResponse> getChangelog(
        final String groupId, final String artifactId, final String version
    ) {
        return notUsed ->
            this.getChangelogEntity(new StringJoiner(":").add(groupId).add(artifactId).add(version).toString())
                .ask(new ChangelogCommand.GetChangelogFromCoordinates(groupId, artifactId, version));

    }

    @Override
    public ServiceCall<NotUsed, NotUsed> registerArtifact(final Artifact artifact) {
        return notUsed -> this.getChangelogEntity(artifact.getFormattedString(":"))
            .ask(new ChangelogCommand.RegisterArtifact(artifact));
    }

    private PersistentEntityRef<ChangelogCommand> getChangelogEntity(String mavenCoordinates) {
        return this.registry.refFor(ChangelogEntity.class, mavenCoordinates);
    }


}
