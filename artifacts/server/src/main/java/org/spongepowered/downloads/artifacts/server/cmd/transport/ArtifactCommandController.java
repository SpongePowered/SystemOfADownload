/*
 * This file is part of SystemOfADownload, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://spongepowered.org/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.downloads.artifacts.server.cmd.transport;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.mutation.Update;
import org.spongepowered.downloads.artifact.api.query.ArtifactDetails;
import org.spongepowered.downloads.artifact.api.registration.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.registration.Response;
import org.spongepowered.downloads.artifacts.server.cmd.details.ArtifactsService;
import org.spongepowered.downloads.artifacts.server.cmd.details.DetailsCommand;
import org.spongepowered.downloads.artifacts.server.cmd.group.GroupCommand;
import org.spongepowered.downloads.artifacts.server.cmd.group.GroupService;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;

@Controller("/groups/{groupID}/artifacts")
@Requires("command")
public class ArtifactCommandController {

    @Inject
    private GroupService groupService;

    @Inject
    private ArtifactsService artifactsService;


    @Inject
    public ArtifactCommandController() {

    }

    @Post()
    @Transactional(isolation = TransactionDefinition.Isolation.REPEATABLE_READ)
    public HttpResponse<Response> registerArtifact(
        @PathVariable @NotNull final String groupID,
        @Body @NotNull @Valid final ArtifactRegistration.RegisterArtifact registration
    ) {
        return this.groupService.registerArtifact(groupID, new GroupCommand.RegisterArtifact(
            registration.artifactId(),
            registration.displayName()
        ));
    }

    @Patch("/{artifactID}/update")
    public HttpResponse<ArtifactDetails.Response> updateDetails(
        @PathVariable String groupID,
        @PathVariable String artifactID,
        @Body final Update update
    ) {
        groupID = groupID.toLowerCase(Locale.ROOT);
        artifactID = artifactID.toLowerCase(Locale.ROOT);
        final var coords = new ArtifactCoordinates(groupID, artifactID);

        final var response = switch (update) {
            case Update.Website w -> {
                final URL url;
                try {
                    url = new URI(w.website()).toURL();
                } catch (URISyntaxException | MalformedURLException e) {
                    yield new ArtifactDetails.Response.Error("url is invalid");
                }
                yield this.artifactsService.updateArtifactDetails(coords, new DetailsCommand.UpdateWebsite(coords, url));
            }
            case Update.DisplayName d ->
                this.artifactsService.updateArtifactDetails(coords, new DetailsCommand.UpdateDisplayName(coords, d.display()));
            case Update.GitRepository gr -> {
                try {
                    var ignored = new URI(gr.gitRepo()).toURL();
                } catch (URISyntaxException | MalformedURLException e) {
                    yield new ArtifactDetails.Response.Error("url is invalid");
                }
                yield this.artifactsService.updateArtifactDetails(coords, new DetailsCommand.UpdateGitRepository(coords, gr.gitRepo()));
            }
            case Update.Issues i -> {
                final URL url;
                try {
                    url = new URI(i.issues()).toURL();
                } catch (URISyntaxException | MalformedURLException e) {
                    yield new ArtifactDetails.Response.Error("url is invalid");
                }
                yield this.artifactsService.updateArtifactDetails(coords, new DetailsCommand.UpdateIssues(coords, url));
            }
            case Update.GitRepositories repos -> {
                final var gitRepos = repos.gitRepos();
                try {
                    for (final var repo : gitRepos) {
                        var ignored = new URI(repo).toURL();
                    }
                } catch (URISyntaxException | MalformedURLException e) {
                    yield new ArtifactDetails.Response.Error("url is invalid");
                }
                yield this.artifactsService.updateArtifactDetails(coords, new DetailsCommand.UpdateGitRepositories(coords, gitRepos));
            }
        };
        return switch (response) {
            case ArtifactDetails.Response.Ok ok -> HttpResponse.ok(ok);
            case ArtifactDetails.Response.Error error -> HttpResponse.badRequest(error);
            case ArtifactDetails.Response.NotFound notFound -> HttpResponse.notFound(notFound);
            case ArtifactDetails.Response.NotModified ignored -> HttpResponse.notModified();
            case ArtifactDetails.Response.ValidRepo repo -> HttpResponse.ok(repo);
        };
    }

}
