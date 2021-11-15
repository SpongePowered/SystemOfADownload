package org.spongepowered.downloads.versions.worker.consumer;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Routers;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.typed.Cluster;
import akka.japi.Pair;
import akka.japi.function.Function;
import akka.stream.javadsl.Flow;
import akka.stream.typed.javadsl.ActorFlow;
import io.vavr.collection.List;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.api.models.ArtifactUpdate;
import org.spongepowered.downloads.versions.api.models.VersionedArtifactUpdates;
import org.spongepowered.downloads.versions.util.akka.FlowUtil;
import org.spongepowered.downloads.versions.util.jgit.CommitResolver;
import org.spongepowered.downloads.versions.worker.WorkerSpawner;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.VersionedArtifactCommand;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.VersionedArtifactEntity;

import java.time.Duration;
import java.util.UUID;

public class VersionedAssetSubscriber {
    public static void setup(ActorContext<Void> ctx, VersionsService versions, ClusterSharding sharding) {

        final var flows = generateFlows(ctx, sharding);

        versions.artifactUpdateTopic()
            .subscribe()
            .atLeastOnce(flows.versionedArtifactUpdateConsumer);

        versions.versionedArtifactUpdatesTopic()
            .subscribe()
            .atLeastOnce(flows.versionedAssetUpdateConsumer);
    }

    private final record ConsumerFlows(
        Flow<ArtifactUpdate, Done, NotUsed> versionedArtifactUpdateConsumer,
        Flow<VersionedArtifactUpdates, Done, NotUsed> versionedAssetUpdateConsumer
    ) {
    }

    private static ConsumerFlows generateFlows(ActorContext<Void> ctx, ClusterSharding sharding) {

        // region ArtifactVersionRegistered flows
        final var versionedArtifactFlow = artifactVersionRegisteredFlows(sharding);
        // endregion

        // region VersionedAssetUpdate flows
        final var versionedAssetFlow = createVersionedAssetFlows(ctx);
        // endregion

        return new ConsumerFlows(versionedArtifactFlow, versionedAssetFlow);
    }

    private static Flow<VersionedArtifactUpdates, Done, NotUsed> createVersionedAssetFlows(
        ActorContext<Void> ctx
    ) {
        final var member = Cluster.get(ctx.getSystem()).selfMember();
        final ActorRef<CommitResolver.Command> workerRef;
        final var uid = UUID.randomUUID();
        final var workerName = "commit-resolver-" + uid;

        if (member.hasRole("commit-resolver")) {
            final var supervised = Behaviors.supervise(
                    CommitDetailsRegistrar.register()
                )
                .onFailure(SupervisorStrategy.restartWithBackoff(
                    Duration.ofMillis(100),
                    Duration.ofSeconds(40),
                    0.1
                ));
            final var registrar = ctx.spawn(supervised, "commit-details-registrar-" + uid);
            final var pool = Routers.pool(4, CommitResolver.resolveCommit(registrar));
            workerRef = WorkerSpawner.spawnRemotableWorker(
                ctx, () -> pool,
                () -> CommitResolver.SERVICE_KEY,
                () -> workerName
            );
        } else {
            final var group = Routers.group(CommitResolver.SERVICE_KEY);
            workerRef = ctx.spawn(group, workerName);
        }
        final var flow = ActorFlow.<VersionedArtifactUpdates.CommitExtracted, CommitResolver.Command, Done>ask(
            4,
            workerRef,
            Duration.ofMinutes(10),
            (msg, replyTo) -> new CommitResolver.ResolveCommitDetails(
                msg.coordinates(), msg.commit(), List.empty(), replyTo)
        );

        return FlowUtil.splitClassFlows(
            Pair.create(VersionedArtifactUpdates.CommitExtracted.class, flow)
        );
    }

    private static Flow<ArtifactUpdate, Done, NotUsed> artifactVersionRegisteredFlows(ClusterSharding sharding) {
        final var versionedAssetKickoff = Flow.<ArtifactUpdate.ArtifactVersionRegistered>create()
            .map(u -> sharding.entityRefFor(
                        VersionedArtifactEntity.ENTITY_TYPE_KEY,
                        u.coordinates().asStandardCoordinates()
                    )
                    .<Done>ask(
                        replyTo -> new VersionedArtifactCommand.Register(u.coordinates(), replyTo), Duration.ofMinutes(1))
                    .toCompletableFuture()
                    .join()
            );

        final var versionRegisteredFlow = FlowUtil.broadcast(versionedAssetKickoff);

        final var registerAssets = Flow.<ArtifactUpdate.VersionedAssetCollectionUpdated>create()
            .map(registerVersionWithVersionedAssets(sharding));
        return FlowUtil.splitClassFlows(
            Pair.apply(
                ArtifactUpdate.ArtifactVersionRegistered.class,
                versionRegisteredFlow
            ),
            Pair.apply(
                ArtifactUpdate.VersionedAssetCollectionUpdated.class,
                registerAssets
            )
        );
    }

    private static Function<ArtifactUpdate.VersionedAssetCollectionUpdated, Done> registerVersionWithVersionedAssets(
        ClusterSharding sharding
    ) {
        return update -> sharding.entityRefFor(
                VersionedArtifactEntity.ENTITY_TYPE_KEY,
                update.collection().coordinates().asStandardCoordinates()
            )
            .<Done>ask(
                replyTo -> new VersionedArtifactCommand.AddAssets(update.artifacts(), replyTo),
                Duration.ofMinutes(1)
            )
            .toCompletableFuture()
            .join();
    }
}
