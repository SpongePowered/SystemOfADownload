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
import com.lightbend.lagom.javadsl.api.transport.BadRequest;
import com.lightbend.lagom.javadsl.api.transport.NotFound;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.Offset;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.server.ServerServiceCall;
import io.vavr.API;
import io.vavr.Predicates;
import io.vavr.control.Either;
import io.vavr.control.Try;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
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
import org.spongepowered.downloads.artifact.details.ArtifactDetailsEntity;
import org.spongepowered.downloads.artifact.details.DetailsCommand;
import org.spongepowered.downloads.artifact.details.DetailsEvent;
import org.spongepowered.downloads.artifact.errors.GitRemoteValidationException;
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
                    context,
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
    public ServiceCall<ArtifactDetails.Update<?>, ArtifactDetails.Response> updateDetails(
        final String groupId,
        final String artifactId
    ) {
        return this.authorize(AuthUtils.Types.JWT, AuthUtils.Roles.ADMIN, profile -> update -> {
            final var coords = ArtifactServiceImpl.validateCoordinates(groupId, artifactId).get();

            // Java 17 preview feature allows for switch matching here
            // Waiting on SOAD-16
            if (update instanceof ArtifactDetails.Update.Website w) {
                final var validate = w.validate();
                if (validate.isLeft()) {
                    throw validate.getLeft();
                }
                final var validUrl = validate.get();
                final var response = this.getDetailsEntity(groupId, artifactId)
                    .<Either<NotFound, ArtifactDetails.Response>>ask(
                        r -> new DetailsCommand.UpdateWebsite(coords, validUrl, r), this.askTimeout);
                return response.thenApply(Either::get);
            } else if (update instanceof ArtifactDetails.Update.DisplayName d) {
                final var validate = d.validate();
                if (validate.isLeft()) {
                    throw validate.getLeft();
                }
                final var displayName = validate.get();
                final var response = this.getDetailsEntity(groupId, artifactId)
                    .<Either<NotFound, ArtifactDetails.Response>>ask(
                        r -> new DetailsCommand.UpdateDisplayName(coords, displayName, r),
                        this.askTimeout
                    );
                return response.thenApply(Either::get);
            } else if (update instanceof ArtifactDetails.Update.GitRepository gr) {
                final var validate = gr.validate();
                if (validate.isLeft()) {
                    throw validate.getLeft();
                }
                final var invalidRemote = API.Case(
                    API.$(Predicates.instanceOf(InvalidRemoteException.class)),
                    () -> new BadRequest(String.format("Invalid remote: %s", gr.gitRepo()))
                );
                final var genericRemoteProblem = API.Case(
                    API.$(Predicates.instanceOf(GitAPIException.class)),
                    t -> new GitRemoteValidationException("Error resolving repository", t)
                );

                @SuppressWarnings("unchecked") final var of = Try.of(
                        () -> Git.lsRemoteRepository()
                            .setRemote(gr.gitRepo())
                            .call()
                    ).mapFailure(invalidRemote, genericRemoteProblem)
                    .map(refs -> !refs.isEmpty())
                    .flatMap(remoteValid -> {
                        if (!remoteValid) {
                            return Try.failure(new GitRemoteValidationException("Remote repository has no refs"));
                        }
                        return Try.success(gr.gitRepo());
                    })
                    .get();

                final var response = this.getDetailsEntity(groupId, artifactId)
                    .<Either<NotFound, ArtifactDetails.Response>>ask(
                        r -> new DetailsCommand.UpdateGitRepository(coords, of, r), this.askTimeout);
                return response.thenApply(Either::get);
            } else if (update instanceof ArtifactDetails.Update.Issues i) {
                final var validate = i.validate();
                if (validate.isLeft()) {
                    throw validate.getLeft();
                }
                final var validUrl = validate.get();
                final var response = this.getDetailsEntity(groupId, artifactId)
                    .<Either<NotFound, ArtifactDetails.Response>>ask(
                        r -> new DetailsCommand.UpdateIssues(coords, validUrl, r), this.askTimeout);
                return response.thenApply(Either::get);
            } else {
                throw new BadRequest(String.format("Unknown update type: %s", update));
            }
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
                .<ArtifactRegistration.Response>ask(
                    replyTo -> new GroupCommand.RegisterArtifact(artifactId, replyTo), this.askTimeout)
                .thenCompose(response -> {
                    if (!(response instanceof ArtifactRegistration.Response.ArtifactRegistered)) {
                        return CompletableFuture.completedFuture(response);
                    }
                    final var coordinates = ((ArtifactRegistration.Response.ArtifactRegistered) response).coordinates;
                    return this.getDetailsEntity(
                            coordinates.groupId, coordinates.artifactId)
                        .<NotUsed>ask(
                            replyTo -> new DetailsCommand.RegisterArtifact(
                                coordinates, registration.displayName, replyTo), this.askTimeout)
                        .thenApply(notUsed -> response);
                });
        });
    }

    @Override
    public ServiceCall<NotUsed, GroupResponse> getGroup(final String groupId) {
        return notUsed -> this.getGroupEntity(groupId.toLowerCase(Locale.ROOT))
            .ask(replyTo -> new GroupCommand.GetGroup(groupId, replyTo), this.askTimeout);
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

    @Override
    public Topic<ArtifactUpdate> artifactUpdate() {
        return TopicProducer.taggedStreamWithOffset(
            DetailsEvent.TAG.allTags(),
            (AggregateEventTag<DetailsEvent> tag, Offset from) ->
                this.persistentEntityRegistry.eventStream(tag, from)
                    .mapConcat(ArtifactServiceImpl::convertDetailsEvent)
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

    private static List<Pair<ArtifactUpdate, Offset>> convertDetailsEvent(Pair<DetailsEvent, Offset> pair) {
        final ArtifactUpdate message;
        // Java 17+ Switch pattern matching will benefit here
        // Or can use Vavr Match, but that seems overkill.
        if (pair.first() instanceof DetailsEvent.ArtifactGitRepositoryUpdated repoUpdated) {
            message = new ArtifactUpdate.GitRepositoryAssociated(repoUpdated.coordinates(), repoUpdated.gitRepo());
        } else if (pair.first() instanceof DetailsEvent.ArtifactRegistered registered) {
            message = new ArtifactUpdate.ArtifactRegistered(registered.coordinates());
        } else if (pair.first() instanceof DetailsEvent.ArtifactDetailsUpdated details) {
            message = new ArtifactUpdate.DisplayNameUpdated(details.coordinates(), details.displayName());
        } else if (pair.first() instanceof DetailsEvent.ArtifactIssuesUpdated details) {
            message = new ArtifactUpdate.IssuesUpdated(details.coordinates(), details.url());
        } else if (pair.first() instanceof DetailsEvent.ArtifactWebsiteUpdated website) {
            message = new ArtifactUpdate.WebsiteUpdated(website.coordinates(), website.url());
        } else {
            return Collections.emptyList();
        }
        return Collections.singletonList(Pair.create(message, pair.second()));
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
