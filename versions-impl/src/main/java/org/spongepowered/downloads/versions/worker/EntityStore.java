package org.spongepowered.downloads.versions.worker;

import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.persistence.typed.PersistenceId;
import org.spongepowered.downloads.versions.worker.domain.global.GlobalRegistration;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.VersionedArtifactEntity;

public final class EntityStore {

    public static void setupPersistedEntities(ClusterSharding sharding) {
        sharding.init(Entity.of(GlobalRegistration.ENTITY_TYPE_KEY, ctx -> GlobalRegistration.create(ctx.getEntityId(), PersistenceId.of(ctx.getEntityTypeKey().name(), ctx.getEntityId()))));
        sharding.init(Entity.of(VersionedArtifactEntity.ENTITY_TYPE_KEY, VersionedArtifactEntity::create));
    }

}
