package org.spongepowered.downloads.artifacts.server.cmd.transport;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import org.spongepowered.downloads.artifact.api.registration.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.registration.Response;
import org.spongepowered.downloads.artifacts.server.cmd.group.GroupCommand;
import org.spongepowered.downloads.artifacts.server.cmd.group.GroupService;

@Controller("/groups/{groupID}/artifacts")
@Requires("command")
public class ArtifactCommandController {

    @Inject
    private GroupService groupService;


    @Inject
    public ArtifactCommandController() {

    }

    @Post(value = "/")
    @Transactional(isolation = TransactionDefinition.Isolation.REPEATABLE_READ)
    public HttpResponse<Response> registerArtifact(
        @PathVariable @NotNull final String groupID,
        @Body @NotNull final ArtifactRegistration.RegisterArtifact registration
    ) {
        return this.groupService.registerArtifact(groupID, new GroupCommand.RegisterArtifact(
            registration.artifactId(),
            registration.displayName()
        ));
    }
//
//    @Patch("/{artifactID}/update")
//    public CompletionStage<HttpResponse<ArtifactDetails.Response>> updateDetails(
//        @PathVariable String groupID,
//        @PathVariable String artifactID,
//        @Body final Update<?> update
//    ) {
//        final var coords = new ArtifactCoordinates(groupID, artifactID);
//        groupID = groupID.toLowerCase(Locale.ROOT);
//        artifactID = artifactID.toLowerCase(Locale.ROOT);
//        return switch (update) {
//            case Update.Website w -> {
//                final var validate = w.validate();
//                if (validate.isLeft()) {
//                    yield validate.<HttpResponse<ArtifactDetails.Response>>mapLeft(HttpResponse::badRequest)
//                        .mapLeft(CompletableFuture::completedFuture)
//                        .getLeft();
//                }
//                final var validUrl = validate.get();
//                final var response = this.getDetailsEntity(groupID, artifactID)
//                    .<Either<ArtifactDetails.Response.NotFound, ArtifactDetails.Response>>ask(
//                        r -> new DetailsCommand.UpdateWebsite(coords, validUrl, r), this.askTimeout);
//                yield response.thenApply(e -> e.fold(HttpResponse::notFound, HttpResponse::created));
//            }
//            case Update.DisplayName d -> {
//                final var validate = d.validate();
//                if (validate.isLeft()) {
//                    yield validate.<HttpResponse<ArtifactDetails.Response>>mapLeft(HttpResponse::badRequest)
//                        .mapLeft(CompletableFuture::completedFuture)
//                        .getLeft();
//                }
//                final var displayName = validate.get();
//                final var response = this.getDetailsEntity(groupID, artifactID)
//                    .<Either<ArtifactDetails.Response.NotFound, ArtifactDetails.Response>>ask(
//                        r -> new DetailsCommand.UpdateDisplayName(coords, displayName, r), this.askTimeout);
//                yield response.thenApply(e -> e.fold(HttpResponse::notFound, HttpResponse::created));
//            }
//            case Update.GitRepository gr -> {
//                final var validate = gr.validate();
//                if (validate.isLeft()) {
//                    yield validate.<HttpResponse<ArtifactDetails.Response>>mapLeft(HttpResponse::badRequest)
//                        .mapLeft(CompletableFuture::completedFuture)
//                        .getLeft();
//                }
//
//                final var response = this.getDetailsEntity(groupID, artifactID)
//                    .<Either<ArtifactDetails.Response.NotFound, ArtifactDetails.Response>>ask(
//                        r -> new DetailsCommand.UpdateGitRepository(coords, gr.gitRepo(), r), this.askTimeout);
//                yield response.thenApply(e -> e.fold(HttpResponse::badRequest, HttpResponse::created));
//            }
//            case Update.Issues i -> {
//                final var validate = i.validate();
//                if (validate.isLeft()) {
//                    yield validate.<HttpResponse<ArtifactDetails.Response>>mapLeft(HttpResponse::badRequest)
//                        .mapLeft(CompletableFuture::completedFuture)
//                        .getLeft();
//                }
//                final var validUrl = validate.get();
//                final var response = this.getDetailsEntity(groupID, artifactID)
//                    .<Either<ArtifactDetails.Response.NotFound, ArtifactDetails.Response>>ask(
//                        r -> new DetailsCommand.UpdateIssues(coords, validUrl, r), this.askTimeout);
//                yield response.thenApply(e -> e.fold(HttpResponse::badRequest, HttpResponse::created));
//            }
//        };
//    }

}
