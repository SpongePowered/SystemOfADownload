package org.spongepowered.downloads.versions.sonatype;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.stream.javadsl.Flow;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.persistence.ReadSide;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.group.GroupEvent;
import org.spongepowered.downloads.utils.AuthUtils;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.api.models.VersionRegistration;
import org.spongepowered.downloads.versions.sonatype.global.GlobalManagedArtifacts;
import org.spongepowered.downloads.versions.sonatype.global.ManageCommand;
import org.spongepowered.downloads.versions.sonatype.resync.ArtifactSynchronizerAggregate;
import org.spongepowered.downloads.versions.sonatype.resync.Resync;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Singleton
public class SonatypeSynchronizer {

    private final ActorSystem system;
    private final PersistentEntityRegistry registry;
    private final CassandraSession cassandraSession;
    private final ArtifactService artifactService;
    private final VersionsService versionsService;
    private final ClusterSharding clusterSharding;
    private static final Logger LOGGER = LogManager.getLogger(SonatypeSynchronizer.class);

    @Inject
    public SonatypeSynchronizer(
        final ActorSystem system, final PersistentEntityRegistry registry,
        final ArtifactService artifactService,
        final VersionsService versionsService,
        final ClusterSharding clusterSharding,
        final CassandraSession cassandraSession,
        final ReadSide readSide
    ) {
        this.system = system;
        this.registry = registry;
        this.artifactService = artifactService;
        this.versionsService = versionsService;
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
        this.clusterSharding.init(
            Entity.of(
                GlobalManagedArtifacts.ENTITY_TYPE_KEY,
                GlobalManagedArtifacts::create
            )
        );
        this.cassandraSession = cassandraSession;
        this.artifactService.groupTopic()
            .subscribe().atLeastOnce(Flow.fromFunction(this::onGroupEvent));
    }

    private Done onGroupEvent(GroupEvent groupEvent) {
        if (!(groupEvent instanceof GroupEvent.ArtifactRegistered)) {
            return Done.done();
        }
        final String groupId = ((GroupEvent.ArtifactRegistered) groupEvent).groupId;
        final String artifact = ((GroupEvent.ArtifactRegistered) groupEvent).artifact;
        final var coordinates = new ArtifactCoordinates(groupId, artifact);
        return this.getGlobalRegistry()
            .<NotUsed>ask(
                replyTo -> {
                    return new ManageCommand.Add(coordinates, replyTo);
                },
                Duration.ofSeconds(2)
            )
            .thenCompose(notUsed -> this.fetchAndRegisterVersions(coordinates))
            .toCompletableFuture()
            .join();
    }


    private void syncAndUpdateArtifacts() {
        LOGGER.log(Level.INFO, "Ticking");
        this.getGlobalRegistry()
            .ask(ManageCommand.GetAllArtifacts::new, Duration.ofSeconds(1))
            .thenAccept(artifacts -> artifacts.toJavaParallelStream()
                .map(this::fetchAndRegisterVersions)
                .forEach(CompletableFuture::join)
            ).toCompletableFuture()
            .join();
    }

    private CompletableFuture<Done> fetchAndRegisterVersions(ArtifactCoordinates coordinates) {
        return this.getResyncEntity(coordinates.groupId, coordinates.artifactId)
            .ask(Resync::new, Duration.ofSeconds(30))
            .thenCompose(mavenCoordinates -> {
                mavenCoordinates.toJavaParallelStream()
                    .forEach(
                        coords -> this.versionsService.registerArtifactCollection(coords.groupId, coords.artifactId)
                            .handleRequestHeader(
                                requestHeader -> requestHeader.withHeader(AuthUtils.INTERNAL_HEADER_KEY,
                                    AuthUtils.INTERNAL_HEADER_SECRET
                                ))
                            .invoke(new VersionRegistration.Register.Version(coords))
                            .toCompletableFuture()
                            .join());
                return CompletableFuture.completedFuture(Done.done());
            })
            .toCompletableFuture();
    }

    private EntityRef<Resync> getResyncEntity(final String groupId, final String artifactId) {
        return this.clusterSharding.entityRefFor(
            ArtifactSynchronizerAggregate.ENTITY_TYPE_KEY, groupId + ":" + artifactId);
    }

    private EntityRef<ManageCommand> getGlobalRegistry() {
        return this.clusterSharding.entityRefFor(GlobalManagedArtifacts.ENTITY_TYPE_KEY, "global");
    }

}
