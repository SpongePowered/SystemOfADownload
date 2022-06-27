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
package org.spongepowered.downloads.artifact.transport;

import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.japi.Pair;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.transport.NotFound;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.Offset;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.server.ServerServiceCall;
import io.vavr.control.Try;
import org.pac4j.core.config.Config;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.event.ArtifactUpdate;
import org.spongepowered.downloads.artifact.api.event.GroupUpdate;
import org.spongepowered.downloads.artifact.api.query.ArtifactDetails;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.artifact.api.query.GroupsResponse;
import org.spongepowered.downloads.artifact.details.DetailsEvent;
import org.spongepowered.downloads.artifact.details.DetailsManager;
import org.spongepowered.downloads.artifact.global.GlobalManager;
import org.spongepowered.downloads.artifact.group.GroupEvent;
import org.spongepowered.downloads.artifact.group.GroupManager;
import org.spongepowered.downloads.auth.AuthenticatedInternalService;
import org.spongepowered.downloads.auth.SOADAuth;
import org.spongepowered.downloads.auth.api.utils.AuthUtils;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class RestArtifactService implements ArtifactService,
    AuthenticatedInternalService {
    private final Config securityConfig;
    private final PersistentEntityRegistry persistentEntityRegistry;
    private final AuthUtils auth;
    private final DetailsManager details;
    private final GroupManager group;
    private final GlobalManager global;

    @Inject
    public RestArtifactService(
        final ClusterSharding clusterSharding,
        final PersistentEntityRegistry persistentEntityRegistry,
        final AuthUtils auth,
        @SOADAuth final Config securityConfig
    ) {
        this.securityConfig = securityConfig;
        this.auth = auth;
        this.details = new DetailsManager(clusterSharding);
        this.global = new GlobalManager(clusterSharding);
        this.group = new GroupManager(clusterSharding, this.details, this.global);

        this.persistentEntityRegistry = persistentEntityRegistry;
    }

    @Override
    public ServiceCall<NotUsed, GetArtifactsResponse> getArtifacts(final String groupId) {
        return none -> this.group.getArtifacts(groupId);
    }

    @Override
    public ServiceCall<GroupRegistration.RegisterGroupRequest, GroupRegistration.Response> registerGroup() {
        return this.authorize(AuthUtils.Types.JWT, AuthUtils.Roles.ADMIN, profile ->
            this.group::registerGroup);
    }

    @Override
    public ServiceCall<ArtifactDetails.Update<?>, ArtifactDetails.Response> updateDetails(
        final String groupId,
        final String artifactId
    ) {
        return this.authorize(AuthUtils.Types.JWT, AuthUtils.Roles.ADMIN, profile -> update -> {
            final var coords = RestArtifactService.validateCoordinates(groupId, artifactId).get();

            return switch (update) {
                case ArtifactDetails.Update.Website w -> this.details.UpdateWebsite(coords, w);
                case ArtifactDetails.Update.DisplayName d -> this.details.UpdateDisplayName(coords, d);
                case ArtifactDetails.Update.GitRepository gr -> this.details.UpdateGitRepository(coords, gr);
                case ArtifactDetails.Update.Issues i -> this.details.UpdateIssues(coords, i);
            };
        });
    }

    @Override
    public ServerServiceCall<ArtifactRegistration.RegisterArtifact, ArtifactRegistration.Response> registerArtifacts(
        final String groupId
    ) {
        return this.authorize(AuthUtils.Types.JWT, AuthUtils.Roles.ADMIN, p -> reg -> this.group.registerArtifact(reg, groupId));
    }

    @Override
    public ServiceCall<NotUsed, GroupResponse> getGroup(final String groupId) {
        return notUsed -> this.group.get(groupId);
    }

    @Override
    public ServiceCall<NotUsed, GroupsResponse> getGroups() {
        return notUsed -> this.global.getGroups();
    }

    @Override
    public Topic<GroupUpdate> groupTopic() {
        return TopicProducer.taggedStreamWithOffset(
            GroupEvent.TAG.allTags(),
            (AggregateEventTag<GroupEvent> aggregateTag, Offset fromOffset) ->
                this.persistentEntityRegistry.eventStream(aggregateTag, fromOffset)
                    .mapConcat(RestArtifactService::convertEvent)
        );
    }

    @Override
    public Topic<ArtifactUpdate> artifactUpdate() {
        return TopicProducer.taggedStreamWithOffset(
            DetailsEvent.TAG.allTags(),
            (AggregateEventTag<DetailsEvent> tag, Offset from) ->
                this.persistentEntityRegistry.eventStream(tag, from)
                    .mapConcat(RestArtifactService::convertDetailsEvent)
        );
    }

    private static List<Pair<GroupUpdate, Offset>> convertEvent(Pair<GroupEvent, Offset> pair) {
        return switch (pair.first()) {
            case GroupEvent.ArtifactRegistered a -> Collections.singletonList(
                Pair.create(
                    new GroupUpdate.ArtifactRegistered(a.coordinates()),
                    pair.second()
                ));
            case GroupEvent.GroupRegistered g -> Collections.singletonList(
                Pair.create(new GroupUpdate.GroupRegistered(g.groupId, g.name, g.website), pair.second()));
            default -> Collections.emptyList();
        };
    }

    private static List<Pair<ArtifactUpdate, Offset>> convertDetailsEvent(Pair<DetailsEvent, Offset> pair) {
        final ArtifactUpdate message;
        return switch (pair.first()) {
            case DetailsEvent.ArtifactGitRepositoryUpdated repoUpdated:
                message = new ArtifactUpdate.GitRepositoryAssociated(repoUpdated.coordinates(), repoUpdated.gitRepo());
                yield Collections.singletonList(Pair.create(message, pair.second()));
            case DetailsEvent.ArtifactRegistered registered:
                message = new ArtifactUpdate.ArtifactRegistered(registered.coordinates());
                yield Collections.singletonList(Pair.create(message, pair.second()));
            case DetailsEvent.ArtifactDetailsUpdated details:
                message = new ArtifactUpdate.DisplayNameUpdated(details.coordinates(), details.displayName());
                yield Collections.singletonList(Pair.create(message, pair.second()));
            case DetailsEvent.ArtifactIssuesUpdated issues:
                message = new ArtifactUpdate.IssuesUpdated(issues.coordinates(), issues.url());
                yield Collections.singletonList(Pair.create(message, pair.second()));
            case DetailsEvent.ArtifactWebsiteUpdated website:
                message = new ArtifactUpdate.WebsiteUpdated(website.coordinates(), website.url());
                yield Collections.singletonList(Pair.create(message, pair.second()));
            default:
                yield Collections.emptyList();
        };
    }

    @Override
    public Config getSecurityConfig() {
        return this.securityConfig;
    }

    @Override
    public AuthUtils auth() {
        return this.auth;
    }

    private static Try<ArtifactCoordinates> validateCoordinates(final String groupId, final String artifactId) {
        final var validGroupId = groupId.toLowerCase(Locale.ROOT).trim();
        final var validArtifactId = artifactId.toLowerCase(Locale.ROOT).trim();
        if (validGroupId.isEmpty()) {
            return Try.failure(new NotFound("group not found"));
        }
        if (validArtifactId.isEmpty()) {
            return Try.failure(new NotFound("artifact not found"));
        }
        final var coords = new ArtifactCoordinates(validGroupId, validArtifactId);
        return Try.success(coords);
    }
}
