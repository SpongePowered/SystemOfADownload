package org.spongepowered.downloads.artifacts.server.cmd.transport;

import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.persistence.typed.PersistenceId;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.vavr.control.Either;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.mutation.Update;
import org.spongepowered.downloads.artifact.api.query.ArtifactDetails;
import org.spongepowered.downloads.artifact.api.registration.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.registration.Response;
import org.spongepowered.downloads.artifacts.server.cmd.details.ArtifactDetailsEntity;
import org.spongepowered.downloads.artifacts.server.cmd.details.DetailsCommand;
import org.spongepowered.downloads.artifacts.server.cmd.group.GroupCommand;
import org.spongepowered.downloads.artifacts.server.cmd.group.GroupEntity;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Controller("/groups/{groupID}/artifacts")
@Requires("command")
public class ArtifactCommandController {

    private final ClusterSharding sharding;
    private final Duration askTimeout = Duration.ofSeconds(30);

    @Inject
    public ArtifactCommandController(
        final ClusterSharding sharding
    ) {
        this.sharding = sharding;
        sharding.init(
            Entity.of(
                ArtifactDetailsEntity.ENTITY_TYPE_KEY,
                ctx -> ArtifactDetailsEntity.create(
                    ctx, ctx.getEntityId(), PersistenceId.of(ctx.getEntityTypeKey().name(), ctx.getEntityId())
                )
            )
        );
        sharding.init(
            Entity.of(
                GroupEntity.ENTITY_TYPE_KEY,
                GroupEntity::create
            )
        );
    }

    @Post(value = "/")
    public CompletionStage<HttpResponse<Response>> registerArtifact(
        @PathVariable @NotNull final String groupID,
        @Body @NotNull final ArtifactRegistration.RegisterArtifact registration
    ) {
        final var groupEntity = this.getGroupEntity(groupID.toLowerCase(Locale.ROOT));
        final var artifactId = registration.artifactId();
        final var registerArtifact = groupEntity
            .<Response>ask(replyTo1 ->
                new GroupCommand.RegisterArtifact(artifactId, replyTo1), this.askTimeout
            );
        return registerArtifact
            .thenComposeAsync(response -> switch (response) {
                case Response.ArtifactRegistered registered -> this.initializeArtifact(registration, registered)
                    .thenApply(_ -> HttpResponse.created(response));
                case Response.ArtifactAlreadyRegistered already ->
                    CompletableFuture.completedStage(HttpResponse.ok(already));
                case Response.GroupMissing missing -> CompletableFuture.completedStage(HttpResponse.notFound(missing));
            });
    }

    private CompletionStage<NotUsed> initializeArtifact(
        ArtifactRegistration.RegisterArtifact registration, Response.ArtifactRegistered registered
    ) {
        return this.getDetailsEntity(
                registered.groupId(), registered.artifactId())
            .<NotUsed>ask(
                replyTo -> new DetailsCommand.RegisterArtifact(
                    registered.coordinates(), registration.displayName(), replyTo), this.askTimeout);
    }

    @Patch("/{artifactID}/update")
    public CompletionStage<HttpResponse<ArtifactDetails.Response>> updateDetails(
        @PathVariable String groupID,
        @PathVariable String artifactID,
        @Body final Update<?> update
    ) {
        final var coords = new ArtifactCoordinates(groupID, artifactID);
        groupID = groupID.toLowerCase(Locale.ROOT);
        artifactID = artifactID.toLowerCase(Locale.ROOT);
        return switch (update) {
            case Update.Website w -> {
                final var validate = w.validate();
                if (validate.isLeft()) {
                    yield validate.<HttpResponse<ArtifactDetails.Response>>mapLeft(HttpResponse::badRequest)
                        .mapLeft(CompletableFuture::completedFuture)
                        .getLeft();
                }
                final var validUrl = validate.get();
                final var response = this.getDetailsEntity(groupID, artifactID)
                    .<Either<ArtifactDetails.Response.NotFound, ArtifactDetails.Response>>ask(
                        r -> new DetailsCommand.UpdateWebsite(coords, validUrl, r), this.askTimeout);
                yield response.thenApply(e -> e.fold(HttpResponse::notFound, HttpResponse::created));
            }
            case Update.DisplayName d -> {
                final var validate = d.validate();
                if (validate.isLeft()) {
                    yield validate.<HttpResponse<ArtifactDetails.Response>>mapLeft(HttpResponse::badRequest)
                        .mapLeft(CompletableFuture::completedFuture)
                        .getLeft();
                }
                final var displayName = validate.get();
                final var response = this.getDetailsEntity(groupID, artifactID)
                    .<Either<ArtifactDetails.Response.NotFound, ArtifactDetails.Response>>ask(
                        r -> new DetailsCommand.UpdateDisplayName(coords, displayName, r), this.askTimeout);
                yield response.thenApply(e -> e.fold(HttpResponse::notFound, HttpResponse::created));
            }
            case Update.GitRepository gr -> {
                final var validate = gr.validate();
                if (validate.isLeft()) {
                    yield validate.<HttpResponse<ArtifactDetails.Response>>mapLeft(HttpResponse::badRequest)
                        .mapLeft(CompletableFuture::completedFuture)
                        .getLeft();
                }

                final var response = this.getDetailsEntity(groupID, artifactID)
                    .<Either<ArtifactDetails.Response.NotFound, ArtifactDetails.Response>>ask(
                        r -> new DetailsCommand.UpdateGitRepository(coords, gr.gitRepo(), r), this.askTimeout);
                yield response.thenApply(e -> e.fold(HttpResponse::badRequest, HttpResponse::created));
            }
            case Update.Issues i -> {
                final var validate = i.validate();
                if (validate.isLeft()) {
                    yield validate.<HttpResponse<ArtifactDetails.Response>>mapLeft(HttpResponse::badRequest)
                        .mapLeft(CompletableFuture::completedFuture)
                        .getLeft();
                }
                final var validUrl = validate.get();
                final var response = this.getDetailsEntity(groupID, artifactID)
                    .<Either<ArtifactDetails.Response.NotFound, ArtifactDetails.Response>>ask(
                        r -> new DetailsCommand.UpdateIssues(coords, validUrl, r), this.askTimeout);
                yield response.thenApply(e -> e.fold(HttpResponse::badRequest, HttpResponse::created));
            }
        };
    }

    private EntityRef<GroupCommand> getGroupEntity(final String groupId) {
        return this.sharding.entityRefFor(GroupEntity.ENTITY_TYPE_KEY, groupId.toLowerCase(Locale.ROOT));
    }

    private EntityRef<DetailsCommand> getDetailsEntity(final String groupId, final String artifactId) {
        return this.sharding.entityRefFor(
            ArtifactDetailsEntity.ENTITY_TYPE_KEY,
            STR."\{groupId.toLowerCase(Locale.ROOT)}:\{artifactId.toLowerCase(Locale.ROOT)}"
        );
    }

}
