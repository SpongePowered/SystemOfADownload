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
import io.vavr.collection.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupsResponse;
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
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@Singleton
public class SonatypeSynchronizer {

    private final ArtifactService artifactService;
    private final VersionsService versionsService;
    private final ClusterSharding clusterSharding;
    private static final Logger LOGGER = LogManager.getLogger(SonatypeSynchronizer.class);

    @Inject
    public SonatypeSynchronizer(
        final ActorSystem system,
        final ArtifactService artifactService,
        final VersionsService versionsService,
        final ClusterSharding clusterSharding
    ) {
        this.artifactService = artifactService;
        this.versionsService = versionsService;
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
        artifactService.groupTopic()
            .subscribe()
            .atLeastOnce(Flow.fromFunction(this::onGroupEvent));
        system.scheduler()
            .scheduleAtFixedRate(
                Duration.ofMinutes(1), // Don't bother resyncing immediately, just give SOAD a minute to load up
                Duration.ofMinutes(5),   // And check, every 5 minutes
                this::syncAndUpdateArtifacts,
                system.dispatcher()
            );
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
                replyTo -> new ManageCommand.Add(coordinates, replyTo),
                Duration.ofHours(2)
            )
            .thenCompose(notUsed -> this.fetchAndRegisterVersions(CompletableFuture.completedFuture(coordinates)))
            .toCompletableFuture()
            .join();
    }


    private void syncAndUpdateArtifacts() {
        final var start = System.currentTimeMillis();
        LOGGER.log(Level.INFO, "Ticking");
        this.artifactService.getGroups()
            .invoke()
            .thenApply(groups -> ((GroupsResponse.Available) groups).groups)
            .thenApply(groups ->
                groups
                    .map(group -> this.artifactService.getArtifacts(group.groupCoordinates)
                        .invoke()
                        .toCompletableFuture()
                        .thenApply(artifactsResponse -> {
                            if (!(artifactsResponse instanceof GetArtifactsResponse.ArtifactsAvailable)) {
                                return List.<ArtifactCoordinates>empty();
                            }
                            final var artifactIds = ((GetArtifactsResponse.ArtifactsAvailable) artifactsResponse).artifactIds();
                            return artifactIds.map(
                                artifactId -> new ArtifactCoordinates(group.groupCoordinates, artifactId));
                        })
                    )
                    .map(CompletableFuture::join))
            .thenApply(list -> list.flatMap(List::toStream))
            .thenCompose(list -> CompletableFuture.allOf(
                list
                    .map(coordinates -> this.getGlobalRegistry().
                            <NotUsed>ask(replyTo -> new ManageCommand.Add(coordinates, replyTo), Duration.ofMinutes(1))
                            .thenApply(notUsed -> coordinates)
                    )
                    .map(this::fetchAndRegisterVersions)

                    .toJavaArray(CompletableFuture[]::new)
                )
            )
            .toCompletableFuture()
            .join();
        final var end = System.currentTimeMillis();
        final var duration = Duration.ofMillis(end - start);
        LOGGER.log(Level.INFO, "Done, completed in {}", duration);
    }

    private CompletableFuture<Done> fetchAndRegisterVersions(CompletionStage<ArtifactCoordinates> future) {
        return future
            .thenCompose(coordinates -> this.getResyncEntity(coordinates.groupId, coordinates.artifactId)
                .<List<MavenCoordinates>>ask(replyTo -> new Resync(coordinates, replyTo), Duration.ofHours(30))
                .thenCompose(mavenCoordinates -> {
                    mavenCoordinates.toJavaParallelStream()
                        .forEach(
                            coords -> this.versionsService.registerArtifactCollection(coords.groupId, coords.artifactId)
                                .handleRequestHeader(
                                    requestHeader -> requestHeader.withHeader(
                                        AuthUtils.INTERNAL_HEADER_KEY,
                                        AuthUtils.INTERNAL_HEADER_SECRET
                                    ))
                                .invoke(new VersionRegistration.Register.Version(coords))
                                .toCompletableFuture()
                                .join());
                    return CompletableFuture.completedFuture(Done.done());
                })
                .toCompletableFuture()
        ).toCompletableFuture();
    }

    private EntityRef<Resync> getResyncEntity(final String groupId, final String artifactId) {
        return this.clusterSharding.entityRefFor(
            ArtifactSynchronizerAggregate.ENTITY_TYPE_KEY, groupId + ":" + artifactId);
    }

    private EntityRef<ManageCommand> getGlobalRegistry() {
        return this.clusterSharding.entityRefFor(GlobalManagedArtifacts.ENTITY_TYPE_KEY, "global");
    }

}
