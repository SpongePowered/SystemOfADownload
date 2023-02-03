package org.spongepowered.downloads.artifacts.server.details;

import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.persistence.typed.PersistenceId;
import io.micronaut.http.HttpResponse;
import io.vavr.control.Either;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.lib.Ref;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.query.ArtifactDetails;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;

import java.net.URL;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletionStage;

@Singleton
public class DetailsManager {
    private final ClusterSharding clusterSharding;
    private final Duration askTimeout = Duration.ofHours(5);

    @Inject
    public DetailsManager(final ClusterSharding clusterSharding) {
        this.clusterSharding = clusterSharding;
        this.clusterSharding.init(
            Entity.of(
                ArtifactDetailsEntity.ENTITY_TYPE_KEY,
                context -> ArtifactDetailsEntity.create(
                    context,
                    context.getEntityId(),
                    PersistenceId.of(context.getEntityTypeKey().name(), context.getEntityId())
                )
            )
        );
    }

    public CompletionStage<ArtifactDetails.Response> UpdateWebsite(
        ArtifactCoordinates coords, ArtifactDetails.Update.Website w
    ) {

        final Either<BadRequest, URL> validate = w.validate();
        if (validate.isLeft()) {
            throw validate.getLeft();
        }
        final var validUrl = validate.get();
        return this.getDetailsEntity(coords)
            .<HttpResponse<ArtifactDetails.Response>>ask(
                r -> new DetailsCommand.UpdateWebsite(coords, validUrl, r), this.askTimeout)
            .thenApply(Either::get);
    }


    public CompletionStage<ArtifactDetails.Response> UpdateDisplayName(
        ArtifactCoordinates coords, ArtifactDetails.Update.DisplayName d
    ) {
        final Either<BadRequest, String> validate = d.validate();
        if (validate.isLeft()) {
            throw validate.getLeft();
        }
        final var displayName = validate.get();
        return this.getDetailsEntity(coords)
            .<Either<NotFound, ArtifactDetails.Response>>ask(
                r -> new DetailsCommand.UpdateDisplayName(coords, displayName, r),
                this.askTimeout
            )
            .thenApply(Either::get);
    }

    public CompletionStage<ArtifactDetails.Response> UpdateGitRepository(
        final ArtifactCoordinates coords, ArtifactDetails.Update.GitRepository gr
    ) {
        final Either<BadRequest, URL> validate = gr.validate();
        if (validate.isLeft()) {
            throw validate.getLeft();
        }
        final Collection<Ref> refs;
        try {
            refs = Git.lsRemoteRepository()
                .setRemote(gr.gitRepo())
                .call();
        } catch (InvalidRemoteException e) {
            throw new BadRequest(String.format("Invalid remote: %s", gr.gitRepo()));
        } catch (GitAPIException e) {
            throw new BadRequest(String.format("Error resolving repository '%s'", gr.gitRepo()));
        }
        if (refs.isEmpty()) {
            throw new BadRequest(String.format("Remote repository '%s' has no refs", gr.gitRepo()));
        }

        return this.getDetailsEntity(coords)
            .<Either<NotFound, ArtifactDetails.Response>>ask(
                r -> new DetailsCommand.UpdateGitRepository(coords, gr.gitRepo(), r), this.askTimeout)
            .thenApply(Either::get);
    }

    public CompletionStage<ArtifactDetails.Response> UpdateIssues(
        ArtifactCoordinates coords, ArtifactDetails.Update.Issues i
    ) {
        final Either<BadRequest, URL> validate = i.validate();
        if (validate.isLeft()) {
            throw validate.getLeft();
        }
        final var validUrl = validate.get();
        return this.getDetailsEntity(coords)
            .<Either<NotFound, ArtifactDetails.Response>>ask(
                r -> new DetailsCommand.UpdateIssues(coords, validUrl, r), this.askTimeout).thenApply(
                Either::get);
    }

    private EntityRef<DetailsCommand> getDetailsEntity(final ArtifactCoordinates coordinates) {
        return this.getDetailsEntity(coordinates.groupId(), coordinates.artifactId());
    }

    private EntityRef<DetailsCommand> getDetailsEntity(final String groupId, final String artifactId) {
        return this.clusterSharding.entityRefFor(ArtifactDetailsEntity.ENTITY_TYPE_KEY, groupId + ":" + artifactId);
    }

    public CompletionStage<ArtifactRegistration.Response> registerArtifact(
        ArtifactRegistration.Response.ArtifactRegistered registered,
        String displayName
    ) {
        return this.getDetailsEntity(registered.coordinates())
            .<NotUsed>ask(
                replyTo -> new DetailsCommand.RegisterArtifact(
                    registered.coordinates(), displayName, replyTo), this.askTimeout)
            .thenApply(notUsed -> registered);
    }
}
