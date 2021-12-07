package org.spongepowered.synchronizer.versionsync;

import akka.actor.typed.javadsl.ActorContext;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.persistence.typed.PersistenceId;
import org.spongepowered.downloads.auth.api.utils.AuthUtils;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.synchronizer.SonatypeSynchronizer;

public class ArtifactVersionSyncModule {

    public static void setup(
        final ActorContext<SonatypeSynchronizer.Command> ctx,
        final ClusterSharding sharding,
        final AuthUtils authUtils,
        final VersionsService service
    ) {
        sharding.init(Entity.of(
            ArtifactVersionSyncEntity.ENTITY_TYPE_KEY,
            context -> ArtifactVersionSyncEntity.create(authUtils, service,
                PersistenceId.of(
                    context.getEntityTypeKey().name(),
                    context.getEntityId()
                )
            )
        ));
    }
}
