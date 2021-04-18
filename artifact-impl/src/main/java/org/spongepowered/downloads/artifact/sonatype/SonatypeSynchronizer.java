package org.spongepowered.downloads.artifact.sonatype;

import akka.actor.ActorSystem;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.persistence.ReadSide;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.spongepowered.downloads.artifact.api.ArtifactService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;

@Singleton
public class SonatypeSynchronizer {

    private final ActorSystem system;
    private final PersistentEntityRegistry registry;
    private final CassandraSession cassandraSession;
    private final ArtifactService artifactService;
    private final ClusterSharding clusterSharding;
    private final Logger logger;

    @Inject
    public SonatypeSynchronizer(
        final ActorSystem system, final PersistentEntityRegistry registry,
        final ArtifactService artifactService,
        final ClusterSharding clusterSharding,
        final CassandraSession cassandraSession,
        final ReadSide readSide,
        final Logger logger
    ) {
        this.system = system;
        this.registry = registry;
        this.artifactService = artifactService;
        readSide.register(GroupArtifactProcessor.class);
        system.scheduler()
            .scheduleAtFixedRate(
                Duration.ofMinutes(1), // Don't bother resyncing immediately, just give SOAD a minute to load up
                Duration.ofHours(1),   // And check, on the hour
                this::syncAndUpdateArtifacts,
                system.dispatcher()
            );
        this.clusterSharding = clusterSharding;
        this.clusterSharding.init(
            Entity.of(
                ArtifactSynchronizerAggregate.ENTITY_TYPE_KEY,
                ArtifactSynchronizerAggregate::create
            )
        );
        this.cassandraSession = cassandraSession;
        this.logger = logger;
    }


    private void syncAndUpdateArtifacts() {
        this.logger.log(Level.INFO, "Ticking");

    }
}
