package org.spongepowered.downloads.artifacts.server.groups;

import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import com.lightbend.lagom.javadsl.api.transport.NotFound;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.artifacts.server.details.DetailsManager;
import org.spongepowered.downloads.artifacts.server.global.GlobalManager;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public final class GroupManager {
    private final ClusterSharding clusterSharding;
    private final GlobalManager global;
    private final Duration askTimeout = Duration.ofHours(5);
    private final DetailsManager details;

    @Inject
    public GroupManager(ClusterSharding clusterSharding, final DetailsManager details, final GlobalManager global) {
        this.clusterSharding = clusterSharding;
        this.global = global;
        this.details = details;
        this.clusterSharding.init(
            Entity.of(
                GroupEntity.ENTITY_TYPE_KEY,
                GroupEntity::create
            )
        );
    }

    public CompletionStage<GroupRegistration.Response> registerGroup(
        GroupRegistration.RegisterGroupRequest registration
    ) {
        final String mavenCoordinates = registration.groupCoordinates();
        final String name = registration.name();
        final String website = registration.website();
        return this.groupEntity(registration.groupCoordinates().toLowerCase(Locale.ROOT))
            .<GroupRegistration.Response>ask(
                replyTo -> new GroupCommand.RegisterGroup(mavenCoordinates, name, website, replyTo),
                this.askTimeout
            ).thenCompose(response -> {
                if (!(response instanceof GroupRegistration.Response.GroupRegistered registered)) {
                    return CompletableFuture.completedFuture(response);
                }
                return this.global.registerGroup(registered);

            });
    }


    private EntityRef<GroupCommand> groupEntity(final String groupId) {
        return this.clusterSharding.entityRefFor(GroupEntity.ENTITY_TYPE_KEY, groupId.toLowerCase(Locale.ROOT));
    }

    public CompletionStage<ArtifactRegistration.Response> registerArtifact(
        ArtifactRegistration.RegisterArtifact reg, String groupId
    ) {
        final var sanitizedGroupId = groupId.toLowerCase(Locale.ROOT);
        return this.groupEntity(sanitizedGroupId)
            .<ArtifactRegistration.Response>ask(
                replyTo -> new GroupCommand.RegisterArtifact(reg.artifactId(), replyTo), this.askTimeout)
            .thenCompose(response -> switch (response) {
                case ArtifactRegistration.Response.GroupMissing missing ->
                    throw new NotFound(String.format("group %s does not exist", missing.s()));

                case ArtifactRegistration.Response.ArtifactRegistered registered ->
                    this.details.registerArtifact(registered, reg.displayName());

                default -> CompletableFuture.completedFuture(response);
            });
    }

    public CompletionStage<GetArtifactsResponse> getArtifacts(String groupId) {
        return this.groupEntity(groupId)
            .<GetArtifactsResponse>ask(replyTo -> new GroupCommand.GetArtifacts(groupId, replyTo), this.askTimeout)
            .thenApply(response -> switch (response) {
                case GetArtifactsResponse.GroupMissing m -> throw new NotFound(String.format("group '%s' not found", m.groupRequested()));
                case GetArtifactsResponse.ArtifactsAvailable a -> a;
            });
    }

    public CompletionStage<GroupResponse> get(String groupId) {
        return this.groupEntity(groupId.toLowerCase(Locale.ROOT))
            .<GroupResponse>ask(replyTo -> new GroupCommand.GetGroup(groupId, replyTo), this.askTimeout)
            .thenApply(response -> switch (response) {
                case GroupResponse.Missing m -> throw new NotFound(String.format("group '%s' not found", m.groupId()));
                case GroupResponse.Available a -> a;
            });
    }
}
