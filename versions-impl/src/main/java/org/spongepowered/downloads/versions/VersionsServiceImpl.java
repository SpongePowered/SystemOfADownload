package org.spongepowered.downloads.versions;

import akka.Done;
import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.stream.javadsl.Flow;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.deser.ExceptionMessage;
import com.lightbend.lagom.javadsl.api.transport.NotFound;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.lightbend.lagom.javadsl.api.transport.TransportException;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.server.ServerServiceCall;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pac4j.core.config.Config;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.group.GroupEvent;
import org.spongepowered.downloads.auth.AuthenticatedInternalService;
import org.spongepowered.downloads.auth.api.SOADAuth;
import org.spongepowered.downloads.utils.AuthUtils;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.api.event.VersionedArtifactEvent;
import org.spongepowered.downloads.versions.api.models.GetVersionResponse;
import org.spongepowered.downloads.versions.api.models.GetVersionsResponse;
import org.spongepowered.downloads.versions.api.models.TagRegistration;
import org.spongepowered.downloads.versions.api.models.VersionRegistration;
import org.spongepowered.downloads.versions.collection.ACCommand;
import org.spongepowered.downloads.versions.collection.VersionedArtifactAggregate;
import org.taymyr.lagom.javadsl.openapi.AbstractOpenAPIService;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

public class VersionsServiceImpl extends AbstractOpenAPIService implements VersionsService,
    AuthenticatedInternalService {
    private static final Logger LOGGER = LogManager.getLogger("VersionService");
    private final PersistentEntityRegistry persistentEntityRegistry;
    private final Config securityConfig;
    private final ClusterSharding clusterSharding;
    private final ArtifactService artifactService;
    private final Duration streamTimeout = Duration.ofHours(30);

    @Inject
    public VersionsServiceImpl(
        final ClusterSharding clusterSharding,
        final ArtifactService artifactService,
        final PersistentEntityRegistry persistentEntityRegistry,
        @SOADAuth final Config securityConfig
    ) {
        this.clusterSharding = clusterSharding;
        this.persistentEntityRegistry = persistentEntityRegistry;
        this.securityConfig = securityConfig;

        this.clusterSharding.init(
            Entity.of(
                VersionedArtifactAggregate.ENTITY_TYPE_KEY,
                VersionedArtifactAggregate::create
            )
        );
        this.artifactService = artifactService;
        this.artifactService.groupTopic()
            .subscribe()
            .atLeastOnce(Flow.<GroupEvent>create().map(this::processGroupEvent));
    }

    private Done processGroupEvent(GroupEvent a) {
        if (!(a instanceof GroupEvent.ArtifactRegistered g)) {
            return Done.done();
        }
        final var groupId = g.groupId.toLowerCase(Locale.ROOT);
        final var artifact = g.artifact.toLowerCase(Locale.ROOT);
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
    public ServerServiceCall<NotUsed, GetVersionsResponse> getArtifactVersions(
        final String groupId,
        final String artifactId,
        final Optional<String> tags,
        final Optional<Integer> limit,
        final Optional<Integer> offset
    ) {
        final String sanitizedGroupId = groupId.toLowerCase(Locale.ROOT);
        final String sanitizedArtifactId = artifactId.toLowerCase(Locale.ROOT);
        tags.ifPresent(tag -> {
            LOGGER.log(Level.INFO, "Tags is {}", tag);
        });
        return notUsed -> this.getCollection(sanitizedGroupId, sanitizedArtifactId)
            .<GetVersionsResponse>ask(
                replyTo -> new ACCommand.GetVersions(sanitizedGroupId, sanitizedArtifactId, tags, limit, offset, replyTo),
                this.streamTimeout
            ).thenApply(response -> {
                if (response instanceof GetVersionsResponse.ArtifactUnknown a) {
                    throw new NotFound(String.format("unknown artifact: %s", a.artifactId()));
                }
                if (response instanceof GetVersionsResponse.GroupUnknown g) {
                    throw new NotFound(String.format("unknown group: %s", g.groupId()));
                }
                if (!(response instanceof GetVersionsResponse.VersionsAvailable v)) {
                    throw new TransportException(TransportErrorCode.InternalServerError, new ExceptionMessage("Something went wrong", "bad response"));
                }

                return v;
            });
    }

    @Override
    public ServiceCall<NotUsed, GetVersionResponse> getArtifactVersion(
        final String groupId,
        final String artifactId,
        final String version
    ) {
        final String sanitizedGroupId = groupId.toLowerCase(Locale.ROOT);
        final String sanitizedArtifactId = artifactId.toLowerCase(Locale.ROOT);
        return notUsed ->
            this.getCollection(sanitizedGroupId, sanitizedArtifactId)
                .<GetVersionResponse>ask(
                    replyTo -> new ACCommand.GetSpecificVersion(sanitizedGroupId, sanitizedArtifactId, version,
                        replyTo
                    ), this.streamTimeout)
                .thenApply(response -> {
                    if (response instanceof GetVersionResponse.VersionMissing m) {
                        throw new NotFound(String.format("version missing %s", m.version()));
                    }
                    return response;
                });
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
                    .ask(
                        replyTo -> new ACCommand.RegisterVersion(v.coordinates, replyTo),
                        this.streamTimeout
                    );
            }
            throw new NotFound("group missing");
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
                .ask(replyTo -> new ACCommand.RegisterArtifactTag(registration.entry(), replyTo), this.streamTimeout);
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
                .ask(replyTo -> new ACCommand.UpdateArtifactTag(registration.entry(), replyTo), this.streamTimeout);
        });
    }

    @Override
    public Topic<VersionedArtifactEvent> topic() {
        return TopicProducer.taggedStreamWithOffset(
            VersionedArtifactEvent.TAG.allTags(),
            this.persistentEntityRegistry::eventStream
        );
    }

    private EntityRef<ACCommand> getCollection(final String groupId, final String artifactId) {
        return this.clusterSharding.entityRefFor(
            VersionedArtifactAggregate.ENTITY_TYPE_KEY, groupId + ":" + artifactId);
    }
}
