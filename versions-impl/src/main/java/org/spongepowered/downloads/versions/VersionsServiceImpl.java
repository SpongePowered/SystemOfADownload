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
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.server.ServerServiceCall;
import org.pac4j.core.config.Config;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.versions.api.event.VersionedArtifactEvent;
import org.spongepowered.downloads.versions.api.models.GetVersionsResponse;
import org.spongepowered.downloads.artifact.group.GroupEvent;
import org.spongepowered.downloads.auth.AuthenticatedInternalService;
import org.spongepowered.downloads.auth.api.SOADAuth;
import org.spongepowered.downloads.utils.AuthUtils;
import org.spongepowered.downloads.versions.api.models.VersionRegistration;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.collection.ACCommand;
import org.spongepowered.downloads.versions.collection.VersionedArtifactAggregate;
import org.taymyr.lagom.javadsl.openapi.AbstractOpenAPIService;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class VersionsServiceImpl  extends AbstractOpenAPIService implements VersionsService,
    AuthenticatedInternalService {
    private final PersistentEntityRegistry persistentEntityRegistry;
    private final Config securityConfig;
    private final ClusterSharding clusterSharding;
    private final Duration streamTimeout = Duration.ofSeconds(30);

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
        artifactService.groupTopic()
            .subscribe()
            .atLeastOnce(Flow.fromFunction(this::processGroupEvent));
    }

    private Done processGroupEvent(GroupEvent a) {
        if (!(a instanceof GroupEvent.ArtifactRegistered)) {
            return Done.done();
        }
        final String groupId = ((GroupEvent.ArtifactRegistered) a).groupId.toLowerCase(Locale.ROOT);
        final String artifact = ((GroupEvent.ArtifactRegistered) a).artifact.toLowerCase(Locale.ROOT);
        return this.getCollection(groupId, artifact)
            .<NotUsed>ask(replyTo -> new ACCommand.RegisterArtifact(new ArtifactCoordinates(groupId, artifact), replyTo), this.streamTimeout)
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
        final String artifactId
    ) {
        final String sanitizedGroupId = groupId.toLowerCase(Locale.ROOT);
        final String sanitizedArtifactId = artifactId.toLowerCase(Locale.ROOT);
        return notUsed -> this.getCollection(sanitizedGroupId, sanitizedArtifactId)
            .ask(replyTo -> new ACCommand.GetVersions(sanitizedGroupId, sanitizedArtifactId, replyTo), this.streamTimeout);
    }

    @Override
    public ServiceCall<VersionRegistration.Register, VersionRegistration.Response> registerArtifactCollection(
        final String groupId,
        final String artifactId
    ) {
        return this.authorize(AuthUtils.Types.JWT, AuthUtils.Roles.ADMIN, profile -> registration -> {
            final String sanitizedGroupId = groupId.toLowerCase(Locale.ROOT);
            final String sanitizedArtifactId = artifactId.toLowerCase(Locale.ROOT);
            if (registration instanceof VersionRegistration.Register.Collection) {
                return this.getCollection(sanitizedGroupId, sanitizedArtifactId)
                    .ask(replyTo -> new ACCommand.RegisterCollection(((VersionRegistration.Register.Collection) registration).collection, replyTo), this.streamTimeout);
            } else if (registration instanceof VersionRegistration.Register.Version) {
                return this.getCollection(sanitizedGroupId, sanitizedArtifactId)
                    .ask(replyTo -> new ACCommand.RegisterVersion(((VersionRegistration.Register.Version) registration).coordinates, replyTo), this.streamTimeout);
            }
            return CompletableFuture.completedFuture(new VersionRegistration.Response.GroupMissing(groupId));
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
