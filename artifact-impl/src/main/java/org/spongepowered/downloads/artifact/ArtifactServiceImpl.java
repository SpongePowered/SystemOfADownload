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
package org.spongepowered.downloads.artifact;

import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.server.ServerServiceCall;
import io.vavr.collection.List;
import io.vavr.control.Either;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.pac4j.core.config.Config;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GetTaggedArtifacts;
import org.spongepowered.downloads.artifact.api.query.GetVersionsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.artifact.collection.ACCommand;
import org.spongepowered.downloads.artifact.collection.ArtifactCollectionEntity;
import org.spongepowered.downloads.artifact.group.GroupCommand;
import org.spongepowered.downloads.artifact.group.GroupEntity;
import org.spongepowered.downloads.artifact.tags.TaggedCommand;
import org.spongepowered.downloads.artifact.tags.TaggedVersionEntity;
import org.spongepowered.downloads.auth.AuthenticatedInternalService;
import org.spongepowered.downloads.auth.api.SOADAuth;
import org.spongepowered.downloads.utils.AuthUtils;
import org.taymyr.lagom.javadsl.openapi.AbstractOpenAPIService;

import java.time.Duration;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class ArtifactServiceImpl extends AbstractOpenAPIService implements ArtifactService,
    AuthenticatedInternalService {

    private static final Logger LOGGER = LogManager.getLogger(ArtifactServiceImpl.class);
    private static final Marker SERVICE_REQUEST = MarkerManager.getMarker("SERVICE");
    private final Duration askTimeout = Duration.ofSeconds(5);
    private final Config securityConfig;
    private final ClusterSharding clusterSharding;

    @Inject
    public ArtifactServiceImpl(
        final ClusterSharding clusterSharding,
        @SOADAuth final Config securityConfig
    ) {
        this.clusterSharding = clusterSharding;
        this.securityConfig = securityConfig;
        this.clusterSharding.init(
            Entity.of(
                GroupEntity.ENTITY_TYPE_KEY,
                GroupEntity::create
            )
        );
        this.clusterSharding.init(
            Entity.of(
                ArtifactCollectionEntity.ENTITY_TYPE_KEY,
                ArtifactCollectionEntity::create
            )
        );
        this.clusterSharding.init(
            Entity.of(
                TaggedVersionEntity.ENTITY_TYPE_KEY,
                TaggedVersionEntity::create
            )
        );
    }

    @Override
    public ServiceCall<NotUsed, GetArtifactsResponse> getArtifacts(final String groupId) {
        return none -> {
            LOGGER.log(Level.DEBUG, String.format("Requesting artifacts for group id: %s", groupId));
            return this.getGroupEntity(groupId)
                .ask(replyTo -> new GroupCommand.GetArtifacts(groupId, replyTo), this.askTimeout);
        };
    }

    @Override
    public ServiceCall<NotUsed, GetVersionsResponse> getArtifactVersions(
        final String groupId,
        final String artifactId
    ) {
        return notUsed -> {
            LOGGER.log(Level.DEBUG, String.format("Requesting versions for artifact: %s:%s", groupId, artifactId));
            return this.authorizeInvoke(this.getGroup(groupId))
                .invoke()
                .thenCompose(response -> {
                    if (response instanceof GroupResponse.Missing) {
                        return CompletableFuture.completedFuture(new GetVersionsResponse.GroupUnknown(groupId));
                    }
                    if (response instanceof GroupResponse.Available) {
                        final var aggregateCoordinates = ((GroupResponse.Available) response).group.groupCoordinates + ":" + artifactId;
                        return this.getCollection(aggregateCoordinates)
                            .<GetVersionsResponse>ask(
                                replyTo -> new ACCommand.GetVersions(groupId, artifactId, replyTo), this.askTimeout);
                    }
                    return CompletableFuture.completedFuture(new GetVersionsResponse.ArtifactUnknown(artifactId));
                });
        };
    }

    @Override
    public ServiceCall<GetTaggedArtifacts.Request, GetTaggedArtifacts.Response> getTaggedArtifacts(
        final String groupId,
        final String artifactId
    ) {
        return request -> {
            if (request instanceof GetTaggedArtifacts.Request.MavenVersion) {
                final var mvn = (GetTaggedArtifacts.Request.MavenVersion) request;
                final String mavenCoordinates = groupId + ":" + artifactId;
                final String tagValue = mvn.getTagType() + ":" + mvn.versionPart();
                return this.getTaggedCollection(mavenCoordinates, tagValue)
                    .ask(replyTo -> new TaggedCommand.RequestTaggedVersions(-1, -1, replyTo), this.askTimeout);
            } else if (request instanceof GetTaggedArtifacts.Request.SnapshotBuilds) {
                final var snapshot = (GetTaggedArtifacts.Request.SnapshotBuilds) request;
                final String mavenCoordinates = groupId + ":" + artifactId;
                final String tagValue = snapshot.getTagType() + ":" + snapshot.mavenVersion();
                return this.getTaggedCollection(mavenCoordinates, tagValue)
                    .ask(replyTo -> new TaggedCommand.RequestTaggedVersions(-1, -1, replyTo), this.askTimeout);
            }
            return CompletableFuture.supplyAsync(
                () -> new GetTaggedArtifacts.Response.TagUnknown(request.getTagType()));
        };
    }

    @Override
    public ServiceCall<GroupRegistration.RegisterGroupRequest, GroupRegistration.Response> registerGroup() {
        return this.authorize(AuthUtils.Types.JWT, AuthUtils.Roles.ADMIN, profile -> registration -> {
            final String mavenCoordinates = registration.groupCoordinates;
            final String name = registration.name;
            final String website = registration.website;
            return this.getGroupEntity(registration.groupCoordinates.toLowerCase(Locale.ROOT))
                .ask(
                    replyTo -> new GroupCommand.RegisterGroup(mavenCoordinates, name, website, replyTo),
                    this.askTimeout
                );
        });
    }

    @Override
    public ServerServiceCall<ArtifactRegistration.RegisterArtifact, ArtifactRegistration.Response> registerArtifacts(
        final String groupId
    ) {
        return this.authorize(AuthUtils.Types.JWT, AuthUtils.Roles.ADMIN, profile -> registration -> {
            final EntityRef<GroupCommand> groupEntity = this.getGroupEntity(
                groupId.toLowerCase(Locale.ROOT));
            final StringJoiner joiner = new StringJoiner(",", "[", "]");
            final var artifactId = registration.artifactId;
            LOGGER.log(
                Level.DEBUG,
                String.format(
                    "Requesting registration of collection %s with artifacts: %s", groupId,
                    joiner
                )
            );
            return groupEntity
                .<ArtifactRegistration.Response>ask(
                    replyTo -> new GroupCommand.RegisterArtifact(artifactId, registration.version, replyTo),
                    this.askTimeout
                )
                .thenApply(this.parseArtifactRegistrationResponseToEither(groupId, artifactId))
                .thenCompose(either ->
                    either.map(this.registerArtifactForCoordinates(groupId, artifactId))
                        .mapLeft(CompletableFuture::completedFuture)
                        .fold(Function.identity(), Function.identity())
                );
        });

    }

    @Override
    public ServiceCall<ArtifactRegistration.RegisterCollection, ArtifactRegistration.Response> registerArtifactCollection(
        final String groupId,
        final String artifactId
    ) {
        return this.authorize(AuthUtils.Types.JWT, AuthUtils.Roles.ADMIN, profile -> registration -> {
            final EntityRef<GroupCommand> groupEntity = this.getGroupEntity(
                groupId.toLowerCase(Locale.ROOT));
            final StringJoiner joiner = new StringJoiner(",", "[", "]");
            LOGGER.log(
                Level.DEBUG,
                String.format(
                    "Requesting registration of collection %s with artifacts: %s", groupId,
                    joiner
                )
            );
            return groupEntity
                .<GetArtifactsResponse>ask(replyTo -> new GroupCommand.GetArtifacts(groupId, replyTo), this.askTimeout)
                .thenApply(this.parseAndVerifyCollectionRegistration(groupId, artifactId,
                    registration.collection.coordinates.version
                ))
                .thenCompose(either ->
                    either.map(this.registerArtifactCollectionInternally(groupId, artifactId, registration))
                        .mapLeft(CompletableFuture::completedFuture)
                        .fold(Function.identity(), Function.identity())
                );
        });
    }

    private Function<MavenCoordinates, CompletionStage<ArtifactRegistration.Response>> registerArtifactCollectionInternally(
        final String groupId, final String artifactId, final ArtifactRegistration.RegisterCollection registration
    ) {
        return coordinates -> {
            LOGGER.log(
                Level.INFO,
                SERVICE_REQUEST,
                "Artifact registered under {}:{}:{}",
                groupId,
                artifactId,
                coordinates.version

            );
            return this.getCollection(coordinates)
                .<NotUsed>ask(
                    replyTo -> new ACCommand.RegisterCollection(coordinates, registration.collection, replyTo), this.askTimeout)
                .thenApply(
                    notUsed -> (ArtifactRegistration.Response) new ArtifactRegistration.Response.RegisteredArtifact(
                        coordinates));

        };
    }

    private Function<ArtifactRegistration.Response, Either<ArtifactRegistration.Response, MavenCoordinates>> parseArtifactRegistrationResponseToEither(
        final String groupId, final String artifactId
    ) {
        return response -> {
            LOGGER.log(
                Level.INFO,
                String.format(
                    "Response for given group %s is %s",
                    groupId,
                    response
                )
            );
            if (response instanceof ArtifactRegistration.Response.GroupMissing) {
                LOGGER.log(
                    Level.DEBUG,
                    String.format(
                        "Requested %s but group was missing",
                        groupId
                    )
                );
                return Either.left(response);
            }
            if (response instanceof ArtifactRegistration.Response.ArtifactAlreadyRegistered) {
                LOGGER.log(
                    Level.DEBUG,
                    String.format(
                        "Group %s already has artifact %s registered",
                        groupId,
                        artifactId
                    )
                );
                return Either.left(response);
            }
            if (response instanceof ArtifactRegistration.Response.RegisteredArtifact) {
                return Either.right(
                    ((ArtifactRegistration.Response.RegisteredArtifact) response).coordinates);
            }
            return Either.left(response);
        };
    }

    private Function<GetArtifactsResponse, Either<ArtifactRegistration.Response, MavenCoordinates>> parseAndVerifyCollectionRegistration(
        final String groupId, final String artifactId, final String version
    ) {
        return response -> {
            LOGGER.log(
                Level.INFO,
                String.format(
                    "Response for given group %s is %s",
                    groupId,
                    response
                )
            );
            if (response instanceof GetArtifactsResponse.GroupMissing) {
                return Either.left(new ArtifactRegistration.Response.GroupMissing(groupId));
            }
            if (response instanceof GetArtifactsResponse.ArtifactsAvailable) {
                if (((GetArtifactsResponse.ArtifactsAvailable) response).artifactIds().contains(version)) {
                    return Either.left(
                        new ArtifactRegistration.Response.ArtifactAlreadyRegistered(artifactId, groupId));
                }
                return Either.right(MavenCoordinates.parse(groupId + ":" + artifactId + ":" + version));
            }
            return Either.left(new ArtifactRegistration.Response.GroupMissing(groupId));
        };
    }

    private Function<MavenCoordinates, CompletionStage<ArtifactRegistration.Response>> registerArtifactForCoordinates(
        final String groupId, final String artifactId
    ) {
        return coordinates -> {
            LOGGER.log(
                Level.INFO,
                SERVICE_REQUEST,
                "Collection registered under {}:{}:{}",
                groupId,
                artifactId,
                coordinates.version

            );
            return this.getCollection(coordinates)
                .<NotUsed>ask(
                    replyTo -> new ACCommand.RegisterArtifact(coordinates, replyTo), this.askTimeout)
                .thenApply(
                    notUsed -> (ArtifactRegistration.Response) new ArtifactRegistration.Response.RegisteredArtifact(
                        coordinates));

        };
    }

    @Override
    public ServiceCall<NotUsed, GroupResponse> getGroup(final String groupId) {
        return notUsed -> {
            LOGGER.log(Level.INFO, String.format("Requesting group by id: %s", groupId));
            return this.getGroupEntity(groupId.toLowerCase(Locale.ROOT))
                .ask(replyTo -> new GroupCommand.GetGroup(groupId, replyTo), this.askTimeout);
        };
    }

    @Override
    public ServiceCall<NotUsed, NotUsed> registerTaggedVersion(
        final String groupAndArtifactId, final String pomVersion
    ) {
        return this.authorize(AuthUtils.Types.JWT, AuthUtils.Roles.ADMIN, profile -> notUsed -> {
            LOGGER.log(
                Level.DEBUG, String.format("Registering Tagged version: %s with maven artifact %s", pomVersion,
                    groupAndArtifactId
                ));
            final List<String> versions;
            final String tagType = pomVersion.endsWith("-SNAPSHOT") ? "snapshot" : "version";
            if (pomVersion.endsWith("-SNAPSHOT")) {
                versions = List.of(pomVersion);
            } else {
                final String[] split = pomVersion.split("\\.");
                versions = List.of(
                    split[0],
                    split[0] + "." + split[1]
                );
            }
            return versions.map(version ->
                this.getTaggedCollection(groupAndArtifactId, tagType + ":" + version)
                    .<NotUsed>ask(replyTo -> new TaggedCommand.RegisterTag(pomVersion, replyTo), this.askTimeout)
            )
                .head();
        });
    }

    private EntityRef<GroupCommand> getGroupEntity(final String groupId) {
        return this.clusterSharding.entityRefFor(GroupEntity.ENTITY_TYPE_KEY, groupId.toLowerCase(Locale.ROOT));
    }

    private EntityRef<ACCommand> getCollection(final MavenCoordinates coordinates) {
        return this.clusterSharding.entityRefFor(
            ArtifactCollectionEntity.ENTITY_TYPE_KEY, coordinates.groupId + ":" + coordinates.artifactId);
    }

    private EntityRef<ACCommand> getCollection(final String mavenCoordinates) {
        return this.clusterSharding.entityRefFor(ArtifactCollectionEntity.ENTITY_TYPE_KEY, mavenCoordinates);
    }

    private EntityRef<TaggedCommand> getTaggedCollection(
        final String mavenGroupAndArtifact, final String tagValue
    ) {
        return this.clusterSharding.entityRefFor(
            TaggedVersionEntity.ENTITY_TYPE_KEY, mavenGroupAndArtifact + "_" + tagValue);
    }

    @Override
    public Config getSecurityConfig() {
        return this.securityConfig;
    }

}
