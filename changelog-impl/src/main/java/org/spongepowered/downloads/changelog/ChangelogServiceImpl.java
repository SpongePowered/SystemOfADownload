package org.spongepowered.downloads.changelog;

import akka.NotUsed;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.persistence.ReadSide;
import io.vavr.Function0;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.changelog.api.ChangelogService;
import org.spongepowered.downloads.changelog.api.query.ChangelogResponse;
import org.spongepowered.downloads.changelog.api.query.GenerateChangelogRequest;
import org.spongepowered.downloads.changelog.client.sonatype.Component;
import org.spongepowered.downloads.changelog.client.sonatype.SonatypeClient;
import org.spongepowered.downloads.git.api.CommitService;
import play.api.Configuration;

import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class ChangelogServiceImpl implements ChangelogService {

    private static final String ENTITY_KEY = ChangelogServiceImpl.class.getName();
    private final PersistentEntityRegistry registry;
    private final ArtifactService artifactService;
    private final CommitService commitService;
    private final Function0<SonatypeClient> client;
    private static final Pattern filePattern = Pattern.compile("(universal\\b|\\d+|shaded).jar$");

    @Inject
    public ChangelogServiceImpl(
        final PersistentEntityRegistry registry,
        final ArtifactService artifactService,
        final CommitService commitService,
        final Configuration configuration,
        final ReadSide readSide
    ) {
        this.registry = registry;
        this.artifactService = artifactService;
        this.commitService = commitService;
        this.client = SonatypeClient.configureClient(configuration);
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
