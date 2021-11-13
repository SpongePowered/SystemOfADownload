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
package org.spongepowered.downloads.versions.server;

import akka.Done;
import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.japi.Pair;
import akka.stream.javadsl.Flow;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.transport.BadRequest;
import com.lightbend.lagom.javadsl.api.transport.NotFound;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.Offset;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.server.ServerServiceCall;
import io.vavr.control.Try;
import org.pac4j.core.config.Config;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.event.GroupUpdate;
import org.spongepowered.downloads.auth.AuthenticatedInternalService;
import org.spongepowered.downloads.auth.SOADAuth;
import org.spongepowered.downloads.auth.api.utils.AuthUtils;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.api.models.ArtifactUpdate;
import org.spongepowered.downloads.versions.api.models.TagRegistration;
import org.spongepowered.downloads.versions.api.models.TagVersion;
import org.spongepowered.downloads.versions.api.models.VersionRegistration;
import org.spongepowered.downloads.versions.api.models.VersionedArtifactUpdates;
import org.spongepowered.downloads.versions.server.collection.ACCommand;
import org.spongepowered.downloads.versions.server.collection.ACEvent;
import org.spongepowered.downloads.versions.server.collection.InvalidRequest;
import org.spongepowered.downloads.versions.server.collection.VersionedArtifactAggregate;
import org.spongepowered.downloads.versions.worker.domain.GitEvent;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionsServiceImpl implements VersionsService,
    AuthenticatedInternalService {
    private final PersistentEntityRegistry persistentEntityRegistry;
    private final Config securityConfig;
    private final ClusterSharding clusterSharding;
    private final Duration streamTimeout = Duration.ofSeconds(30);
    private final AuthUtils auth;

    @Inject
    public VersionsServiceImpl(
        final ClusterSharding clusterSharding,
        final ArtifactService artifactService,
        final PersistentEntityRegistry persistentEntityRegistry,
        @SOADAuth final Config securityConfig,
        final AuthUtils auth
    ) {
        this.clusterSharding = clusterSharding;
        this.persistentEntityRegistry = persistentEntityRegistry;
        this.securityConfig = securityConfig;
        this.auth = auth;

        this.clusterSharding.init(
            Entity.of(
                VersionedArtifactAggregate.ENTITY_TYPE_KEY,
                VersionedArtifactAggregate::create
            )
        );

        artifactService.groupTopic()
            .subscribe()
            .atLeastOnce(Flow.<GroupUpdate>create().map(this::processGroupEvent));

    }

    private Done processGroupEvent(GroupUpdate a) {
        if (!(a instanceof GroupUpdate.ArtifactRegistered g)) {
            return Done.done();
        }
        final var groupId = g.coordinates().groupId.toLowerCase(Locale.ROOT);
        final var artifact = g.coordinates().artifactId.toLowerCase(Locale.ROOT);
        return this.getCollection(groupId, artifact)
            .<NotUsed>ask(
                replyTo -> new ACCommand.RegisterArtifact(new ArtifactCoordinates(groupId, artifact), replyTo),
                this.streamTimeout
            )
            .thenApply(notUsed -> Done.done())
            .toCompletableFuture()
            .join();
    }


    @Override
    public Config getSecurityConfig() {
        return this.securityConfig;
    }

    @Override
    public ServerServiceCall<VersionRegistration.Register, VersionRegistration.Response> registerArtifactCollection(
        final String groupId,
        final String artifactId
    ) {
        return this.authorize(AuthUtils.Types.JWT, AuthUtils.Roles.ADMIN, profile -> registration -> {
            final String sanitizedGroupId = groupId.toLowerCase(Locale.ROOT);
            final String sanitizedArtifactId = artifactId.toLowerCase(Locale.ROOT);
            if (registration instanceof VersionRegistration.Register.Version v) {
                return this.getCollection(sanitizedGroupId, sanitizedArtifactId)
                    .<VersionRegistration.Response>ask(
                        replyTo -> new ACCommand.RegisterVersion(v.coordinates(), replyTo),
                        this.streamTimeout
                    ).thenApply(response -> {
                        if (response instanceof InvalidRequest) {
                            throw new NotFound("unknown artifact or group");
                        }
                        return response;
                    });
            }
            if (registration instanceof VersionRegistration.Register.Collection c) {
                return this.getCollection(sanitizedGroupId, sanitizedArtifactId)
                    .<VersionRegistration.Response>ask(
                        replyTo -> new ACCommand.RegisterCollection(c.collection(), replyTo), this.streamTimeout)
                    .thenApply(response -> {
                        if (response instanceof InvalidRequest) {
                            throw new NotFound("unknown artifact or group");
                        }
                        return response;
                    });
            }
            throw new BadRequest("unknown registration request");
        });
    }

    @Override
    public ServiceCall<TagRegistration.Register, TagRegistration.Response> registerArtifactTag(
        final String groupId,
        final String artifactId
    ) {
        return this.authorize(AuthUtils.Types.JWT, AuthUtils.Roles.ADMIN, profile -> registration -> {
            final String sanitizedGroupId = groupId.toLowerCase(Locale.ROOT);
            final String sanitizedArtifactId = artifactId.toLowerCase(Locale.ROOT);
            return this.getCollection(sanitizedGroupId, sanitizedArtifactId)
                .<TagRegistration.Response>ask(
                    replyTo -> new ACCommand.RegisterArtifactTag(registration.entry(), replyTo), this.streamTimeout)
                .thenApply(response -> {
                    if (response instanceof InvalidRequest) {
                        throw new NotFound("unknown artifact or group");
                    }
                    return response;
                });
        });
    }

    @Override
    public ServiceCall<TagRegistration.Register, TagRegistration.Response> updateArtifactTag(
        final String groupId,
        final String artifactId
    ) {
        return this.authorize(AuthUtils.Types.JWT, AuthUtils.Roles.ADMIN, profile -> registration -> {
            final String sanitizedGroupId = groupId.toLowerCase(Locale.ROOT);
            final String sanitizedArtifactId = artifactId.toLowerCase(Locale.ROOT);
            return this.getCollection(sanitizedGroupId, sanitizedArtifactId)
                .<TagRegistration.Response>ask(
                    replyTo -> new ACCommand.UpdateArtifactTag(registration.entry(), replyTo), this.streamTimeout)
                .thenApply(response -> {
                    if (response instanceof InvalidRequest) {
                        throw new NotFound("unknown artifact or group");
                    }
                    return response;
                });
        });
    }

    @Override
    public ServiceCall<TagVersion.Request, TagVersion.Response> tagVersion(
        final String groupId,
        final String artifactId
    ) {
        return this.authorize(AuthUtils.Types.JWT, AuthUtils.Roles.ADMIN, profile -> request -> {
            if (!(request instanceof TagVersion.Request.SetRecommendationRegex s)) {
                throw new BadRequest("unknown request");
            }
            final var regex = Try.of(() -> Pattern.compile(s.regex()));
            final var validFailures = s.valid()
                .filter(valid -> regex.map(pattern -> pattern.matcher(valid))
                    .mapTry(Matcher::find)
                    .map(b -> !b)
                    .getOrElse(true) // If exception, keep the version as failed
                );
            final var invalidSuccesses = s.invalid()
                .filter(invalid -> regex
                    .map(pattern -> pattern.matcher(invalid))
                    .mapTry(Matcher::find)
                    .getOrElse(true)
                );
            if (!validFailures.isEmpty()) {
                throw new BadRequest("expected valid versions did not match regex: " + validFailures);
            }
            if (!invalidSuccesses.isEmpty()) {
                throw new BadRequest("expected invalid versions matched regex successfully:" + invalidSuccesses);
            }
            return this.getCollection(groupId, artifactId)
                .<TagVersion.Response>ask(
                    replyTo -> new ACCommand.RegisterPromotion(s.regex(), replyTo, s.enableManualMarking()),
                    this.streamTimeout
                )
                .thenApply(response -> {
                    if (response instanceof InvalidRequest) {
                        throw new NotFound("unknown artifact or group");
                    }
                    return response;
                });
        });
    }

    private static List<Pair<ArtifactUpdate, Offset>> convertEvent(Pair<ACEvent, Offset> pair) {
        final ACEvent event = pair.first();
        final ArtifactUpdate update;
        if (event instanceof ACEvent.ArtifactVersionRegistered r) {
            update = new ArtifactUpdate.ArtifactVersionRegistered(r.version);
        } else if (event instanceof ACEvent.ArtifactTagRegistered r) {
            update = new ArtifactUpdate.TagRegistered(r.coordinates(), r.entry());
        } else if (event instanceof ACEvent.VersionedCollectionAdded c) {
            update = new ArtifactUpdate.VersionedAssetCollectionUpdated(
                c.coordinates(), c.collection(), c.newArtifacts());
        } else {
            return Collections.emptyList();
        }
        return List.of(Pair.apply(update, pair.second()));
    }

    @Override
    public Topic<ArtifactUpdate> artifactUpdateTopic() {
        return TopicProducer.taggedStreamWithOffset(
            ACEvent.INSTANCE.allTags(),
            (aggregateTag, fromOffset) -> this.persistentEntityRegistry
                .eventStream(aggregateTag, fromOffset)
                .mapConcat(VersionsServiceImpl::convertEvent)
        );
    }

    @Override
    public Topic<VersionedArtifactUpdates> versionedArtifactUpdatesTopic() {
        return TopicProducer.taggedStreamWithOffset(
            GitEvent.INSTANCE.allTags(),
            (aggregateTag, fromOffset) -> this.persistentEntityRegistry
                .eventStream(aggregateTag, fromOffset)
                .mapConcat(VersionsServiceImpl::convertGitEvents)
        );
    }

    private static List<Pair<VersionedArtifactUpdates, Offset>> convertGitEvents(Pair<GitEvent, Offset> pair) {
        final GitEvent event = pair.first();
        final VersionedArtifactUpdates update;
        if (event instanceof GitEvent.CommitAssociatedWithVersion r) {
            update = new VersionedArtifactUpdates.CommitExtracted(r.coordinates(), r.repository().toList(), r.sha());
        } else if (event instanceof GitEvent.CommitDetailsUpdated r) {
            update = new VersionedArtifactUpdates.GitCommitDetailsAssociated(r.coordinates(), r.commit());
        } else {
            return Collections.emptyList();
        }
        return List.of(Pair.apply(update, pair.second()));
    }
    private EntityRef<ACCommand> getCollection(final String groupId, final String artifactId) {
        return this.clusterSharding.entityRefFor(
            VersionedArtifactAggregate.ENTITY_TYPE_KEY, groupId + ":" + artifactId);
    }

    @Override
    public AuthUtils auth() {
        return this.auth;
    }
}
