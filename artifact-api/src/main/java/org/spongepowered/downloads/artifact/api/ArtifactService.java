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
package org.spongepowered.downloads.artifact.api;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.transport.Method;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GetTaggedArtifacts;
import org.spongepowered.downloads.artifact.api.query.GetVersionsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.utils.AuthUtils;
import org.taymyr.lagom.javadsl.openapi.OpenAPIService;
import org.taymyr.lagom.javadsl.openapi.OpenAPIUtils;

@OpenAPIDefinition(
    info = @Info(
        title = "ArtifactService",
        description = "Manages indexing of Artifacts by a Maven Coordinate oriented fashion",
        contact = @Contact(
            name = "SpongePowered",
            url = "https://spongepowered.org/",
            email = "dev@spongepowered.org"
        ),
        license = @License(
            name = "MIT - The MIT License",
            url = "https://opensource.org/licenses/MIT"
        )
    ),
    tags = @Tag(name = "organization",
        description = "Organization related services")
)
public interface ArtifactService extends OpenAPIService {

    /**
     * Gets the list of available artifacts by their artifact id's given the
     * provided {@code groupId}. The given group id is based off the maven
     * coordinated group id. See
     * <a href=https://maven.apache.org/guides/mini/guide-naming-conventions.html>Maven naming conventions</a>
     *
     * @param groupId The group id by maven convention
     * @return A valid response, whether the group is unregistered or available
     */
    @Operation(
        method = "GET",
        description = "Gets available artifact id's for the given group/organization id",
        parameters = @Parameter(
            name = "name",
            in = ParameterIn.PATH,
            description = "The organization id",
            example = "org.spongepowered"
        ),
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "The registered list of Artifacts by id",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(
                        implementation = GetArtifactsResponse.ArtifactsAvailable.class,
                        description = "The maven artifactid based list of artifacts"
                    )
                )
            ),
            @ApiResponse(
                responseCode = "200",
                description = "The group is unregistered",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(
                        implementation = GetArtifactsResponse.GroupMissing.class,
                        description = "Signifies the group is unregistered with the service. If this is in error, please contact administrators"
                    )
                )
            )
        }
    )
    ServiceCall<NotUsed, GetArtifactsResponse> getArtifacts(String groupId);

    @Operation(
        method = "GET",
        description = "Gets available artifact id's for the given group/organization id",
        parameters = @Parameter(
            name = "name",
            in = ParameterIn.PATH,
            description = "The organization id",
            example = "org.spongepowered"
        ),
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "The registered list of Artifacts by id",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(
                        implementation = GetArtifactsResponse.ArtifactsAvailable.class,
                        description = "The maven artifactid based list of artifacts"
                    )
                )
            ),
            @ApiResponse(
                responseCode = "200",
                description = "The group is unregistered",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(
                        implementation = GetArtifactsResponse.GroupMissing.class,
                        description = "Signifies the group is unregistered with the service. If this is in error, please contact administrators"
                    )
                )
            )
        }
    )
    ServiceCall<NotUsed, GetVersionsResponse> getArtifactVersions(String groupId, String artifactId);

    @Operation(
        method = "POST",
        description = "Gets artifacts in a tagged variation provided by the requested body",
        parameters = {
            @Parameter(
                name = "groupId",
                in = ParameterIn.PATH,
                description = "The maven coordinated groupId",
                example = "org.spongepowered"
            ),
            @Parameter(
                name = "artifactId",
                in = ParameterIn.PATH,
                description = "The maven coordinated artifact id",
                example = "spongevanilla"
            )
        },
        requestBody = @RequestBody(
            description = "The "
        )
    )
    ServiceCall<GetTaggedArtifacts.Request, GetTaggedArtifacts.Response> getTaggedArtifacts(
        String groupId, String artifactId
    );

    @Operation(
        method = "POST",
        description = "Registers new artifact with all of its components",
        requestBody = @RequestBody(
            description = "The body of a collection registration",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(
                    implementation = ArtifactRegistration.RegisterCollection.class,
                    name = "RegisterCollectionObject"
                ),
                examples = {
                    @ExampleObject(
                        name = "registerSpongeVanillaRelease",
                        summary = "Performing a registration of a new SpongeVanilla artifact",
                        value = ""
                    )
                }
            ),
            required = true
        ),
        responses = {
            @ApiResponse(
                content = {
                    @Content(
                        mediaType = "application/json",
                        schema = @Schema(
                            implementation = ArtifactRegistration.Response.ArtifactAlreadyRegistered.class
                        ),
                        examples = @ExampleObject(
                            name = ""
                        )
                    )
                }
            ),
            @ApiResponse(
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(
                        implementation = ArtifactRegistration.Response.RegisteredArtifact.class
                    )
                )
            ),
            @ApiResponse(
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(
                        implementation = ArtifactRegistration.Response.GroupMissing.class
                    )
                )
            )
        },
        security = @SecurityRequirement(
            name = "ldap",
            scopes = {AuthUtils.Roles.ADMIN}
        )
    )
    ServiceCall<ArtifactRegistration.RegisterArtifact, ArtifactRegistration.Response> registerArtifacts(
        String groupId
    );

    ServiceCall<ArtifactRegistration.RegisterCollection, ArtifactRegistration.Response> registerArtifactCollection(
        String groupId, String artifactId
    );

    @Operation(
        method = "POST"
    )
    ServiceCall<GroupRegistration.RegisterGroupRequest, GroupRegistration.Response> registerGroup();

    @Operation(
        method = "GET"
    )
    ServiceCall<NotUsed, GroupResponse> getGroup(String groupId);

    @Operation(
        method = "POST"
    )
    ServiceCall<NotUsed, NotUsed> registerTaggedVersion(String mavenCoordinates, String tagVersion);

    @Override
    default Descriptor descriptor() {
        return OpenAPIUtils.withOpenAPI(Service.named("artifact")
            .withCalls(
                Service.restCall(Method.GET, "/api/:groupId", this::getGroup),
                Service.restCall(Method.GET, "/api/:groupId/artifacts", this::getArtifacts),
                Service.restCall(Method.GET, "/api/:groupId/artifact/:artifactId", this::getArtifactVersions),
                Service.restCall(Method.POST, "/api/:groupId/artifact/:artifactId/", this::getTaggedArtifacts),
                Service.restCall(Method.POST, "/api/:groupId/register", this::registerArtifacts),
                Service.restCall(Method.POST, "/api/:groupId/artifact/:artifactId/register", this::registerArtifactCollection),
                Service.restCall(Method.POST, "/api/admin/groups/create", this::registerGroup)
            )
            .withAutoAcl(true)
        );
    }

}
