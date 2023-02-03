package org.spongepowered.downloads.artifacts.server.global;

import akka.Done;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.persistence.typed.PersistenceId;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupsResponse;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

@Singleton
public final class GlobalManager {
    private final Duration askTimeout = Duration.ofHours(5);

    private final ClusterSharding clusterSharding;

    @Inject
    public GlobalManager(final ClusterSharding clusterSharding) {
        this.clusterSharding = clusterSharding;
        this.clusterSharding.init(
            Entity.of(
                GlobalRegistration.ENTITY_TYPE_KEY,
                ctx -> GlobalRegistration.create(
                    ctx.getEntityId(),
                    PersistenceId.of(ctx.getEntityTypeKey().name(), ctx.getEntityId())
                )
            )
        );
    }

    public CompletionStage<GroupRegistration.Response> registerGroup(
        GroupRegistration.Response.GroupRegistered registered
    ) {
        final Group group = registered.group();
        return this.getGlobalEntity()
            .<Done>ask(replyTo -> new GlobalCommand.RegisterGroup(replyTo, group), this.askTimeout)
            .thenApply(notUsed -> registered);
    }


    private EntityRef<GlobalCommand> getGlobalEntity() {
        return this.clusterSharding.entityRefFor(GlobalRegistration.ENTITY_TYPE_KEY, "global");
    }

    public CompletionStage<GroupsResponse> getGroups() {
        return this.getGlobalEntity().ask(GlobalCommand.GetGroups::new, this.askTimeout);
    }
}
