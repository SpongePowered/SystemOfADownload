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

import akka.Done;
import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.japi.Pair;
import akka.persistence.typed.PersistenceId;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.Offset;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.server.ServerServiceCall;
import org.pac4j.core.config.Config;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.event.GroupUpdate;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.artifact.api.query.GroupsResponse;
import org.spongepowered.downloads.artifact.details.ArtifactDetailsEntity;
import org.spongepowered.downloads.artifact.details.DetailsCommand;
import org.spongepowered.downloads.artifact.global.GlobalCommand;
import org.spongepowered.downloads.artifact.global.GlobalRegistration;
import org.spongepowered.downloads.artifact.group.GroupCommand;
import org.spongepowered.downloads.artifact.group.GroupEntity;
import org.spongepowered.downloads.artifact.group.GroupEvent;
import org.spongepowered.downloads.auth.AuthenticatedInternalService;
import org.spongepowered.downloads.auth.SOADAuth;
import org.spongepowered.downloads.auth.api.utils.AuthUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class ArtifactServiceImpl implements ArtifactService,
    AuthenticatedInternalService {

    private final Duration askTimeout = Duration.ofHours(5);
    private final Config securityConfig;
    private final ClusterSharding clusterSharding;
    private final PersistentEntityRegistry persistentEntityRegistry;
    private final AuthUtils auth;

    @Inject
    public ArtifactServiceImpl(
        final ClusterSharding clusterSharding,
        final PersistentEntityRegistry persistentEntityRegistry,
        final AuthUtils auth,
        @SOADAuth final Config securityConfig
    ) {
        this.clusterSharding = clusterSharding;
        this.securityConfig = securityConfig;
        this.auth = auth;
        this.clusterSharding.init(
            Entity.of(
                GroupEntity.ENTITY_TYPE_KEY,
                GroupEntity::create
            )
        );
        this.clusterSharding.init(
            Entity.of(
                GlobalRegistration.ENTITY_TYPE_KEY,
                ctx -> GlobalRegistration.create(
                    ctx.getEntityId(),
                    PersistenceId.of(ctx.getEntityTypeKey().name(), ctx.getEntityId())
                )
            )
        );
        this.clusterSharding.init(
            Entity.of(
                ArtifactDetailsEntity.ENTITY_TYPE_KEY,
                context -> ArtifactDetailsEntity.create(
                    context.getEntityId(),
                    PersistenceId.of(context.getEntityTypeKey().name(), context.getEntityId())
                )
            )
        );
        this.persistentEntityRegistry = persistentEntityRegistry;
    }

    @Override
    public ServiceCall<NotUsed, GetArtifactsResponse> getArtifacts(final String groupId) {
        return none -> this.getGroupEntity(groupId)
            .ask(replyTo -> new GroupCommand.GetArtifacts(groupId, replyTo), this.askTimeout);
    }

    @Override
    public ServiceCall<GroupRegistration.RegisterGroupRequest, GroupRegistration.Response> registerGroup() {
        return this.authorize(AuthUtils.Types.JWT, AuthUtils.Roles.ADMIN, profile -> registration -> {
            final String mavenCoordinates = registration.groupCoordinates;
            final String name = registration.name;
            final String website = registration.website;
            return this.getGroupEntity(registration.groupCoordinates.toLowerCase(Locale.ROOT))
                .<GroupRegistration.Response>ask(
                    replyTo -> new GroupCommand.RegisterGroup(mavenCoordinates, name, website, replyTo),
                    this.askTimeout
                ).thenCompose(response -> {
                    if (!(response instanceof GroupRegistration.Response.GroupRegistered)) {
                        return CompletableFuture.completedFuture(response);
                    }
                    final var group = ((GroupRegistration.Response.GroupRegistered) response).group();
                    return this.getGlobalEntity()
                        .<Done>ask(replyTo -> new GlobalCommand.RegisterGroup(replyTo, group), this.askTimeout)
                        .thenApply(notUsed -> response);

                });
        });
    }

    @Override
    public ServerServiceCall<ArtifactRegistration.RegisterArtifact, ArtifactRegistration.Response> registerArtifacts(
        final String groupId
    ) {
        return this.authorize(AuthUtils.Types.JWT, AuthUtils.Roles.ADMIN, profile -> registration -> {
            final EntityRef<GroupCommand> groupEntity = this.getGroupEntity(groupId.toLowerCase(Locale.ROOT));
            final var artifactId = registration.artifactId;
            return groupEntity
                .<ArtifactRegistration.Response>ask(replyTo -> new GroupCommand.RegisterArtifact(artifactId, replyTo), this.askTimeout)
                .thenCompose(response -> {
                    if (!(response instanceof ArtifactRegistration.Response.ArtifactRegistered)) {
                       return CompletableFuture.completedFuture(response);
                    }
                    final var coordinates = ((ArtifactRegistration.Response.ArtifactRegistered) response).coordinates;
                    return this.getDetailsEntity(
                        coordinates.groupId, coordinates.artifactId)
                        .<NotUsed>ask(
                            replyTo -> new DetailsCommand.RegisterArtifact(coordinates, registration.displayName, replyTo), this.askTimeout)
                        .thenApply(notUsed -> response);
                });
        });
    }

    @Override
    public ServiceCall<NotUsed, GroupResponse> getGroup(final String groupId) {
        return notUsed -> {
            return this.getGroupEntity(groupId.toLowerCase(Locale.ROOT))
                .ask(replyTo -> new GroupCommand.GetGroup(groupId, replyTo), this.askTimeout);
        };
    }

    @Override
    public ServiceCall<NotUsed, GroupsResponse> getGroups() {
        return notUsed -> this.getGlobalEntity().ask(GlobalCommand.GetGroups::new, this.askTimeout);
    }

    @Override
    public Topic<GroupUpdate> groupTopic() {
        return TopicProducer.taggedStreamWithOffset(
            GroupEvent.TAG.allTags(),
            (AggregateEventTag<GroupEvent> aggregateTag, Offset fromOffset) ->
                this.persistentEntityRegistry.eventStream(aggregateTag, fromOffset)
                    .mapConcat(ArtifactServiceImpl::convertEvent)
        );
    }

    private static List<Pair<GroupUpdate, Offset>> convertEvent(Pair<GroupEvent, Offset> pair) {
        if (pair.first() instanceof GroupEvent.ArtifactRegistered a) {
            return Collections.singletonList(
                Pair.create(
                    new GroupUpdate.ArtifactRegistered(new ArtifactCoordinates(a.groupId, a.artifact)),
                    pair.second()
                ));
        } else if (pair.first() instanceof GroupEvent.GroupRegistered g) {
            return Collections.singletonList(
                Pair.create(new GroupUpdate.GroupRegistered(g.groupId, g.name, g.website), pair.second()));
        }
        return Collections.emptyList();
    }

    private EntityRef<GroupCommand> getGroupEntity(final String groupId) {
        return this.clusterSharding.entityRefFor(GroupEntity.ENTITY_TYPE_KEY, groupId.toLowerCase(Locale.ROOT));
    }

    private EntityRef<DetailsCommand> getDetailsEntity(final String groupId, final String artifactId) {
        return this.clusterSharding.entityRefFor(ArtifactDetailsEntity.ENTITY_TYPE_KEY, groupId + ":" + artifactId);
    }

    private EntityRef<GlobalCommand> getGlobalEntity() {
        return this.clusterSharding.entityRefFor(GlobalRegistration.ENTITY_TYPE_KEY, "global");
    }

    @Override
    public Config getSecurityConfig() {
        return this.securityConfig;
    }

    @Override
    public AuthUtils auth() {
        return this.auth;
    }
}
