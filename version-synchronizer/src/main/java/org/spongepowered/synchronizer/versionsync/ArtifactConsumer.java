package org.spongepowered.synchronizer.versionsync;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Routers;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.stream.javadsl.Flow;
import akka.stream.typed.javadsl.ActorFlow;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.event.GroupUpdate;
import org.spongepowered.downloads.auth.api.utils.AuthUtils;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.synchronizer.SonatypeSynchronizer;
import org.spongepowered.synchronizer.SynchronizerSettings;
import org.spongepowered.synchronizer.actor.ArtifactSyncWorker;

public final class ArtifactConsumer {
    public static void subscribeToArtifactUpdates(
        final ActorContext<SonatypeSynchronizer.Command> context,
        final ArtifactService artifactService,
        final VersionsService versionsService,
        final ClusterSharding clusterSharding,
        final SynchronizerSettings settings
    ) {
        // region Synchronize Artifact Versions from Maven
        final AuthUtils auth = AuthUtils.configure(context.getSystem().settings().config());
        ArtifactVersionSyncModule.setup(context, clusterSharding, auth, versionsService);

        final var syncWorkerBehavior = ArtifactSyncWorker.create(clusterSharding);
        final var pool = Routers.pool(
            settings.reactiveSync.poolSize,
            syncWorkerBehavior
        );

        final var registrationRef = context.spawn(
            pool,
            "group-event-subscriber",
            DispatcherSelector.defaultDispatcher()
        );
        final Flow<GroupUpdate, Done, NotUsed> actorAsk = ActorFlow.ask(
            settings.reactiveSync.parallelism,
            registrationRef, settings.reactiveSync.timeOut,
            (g, b) -> {
                if (!(g instanceof GroupUpdate.ArtifactRegistered a)) {
                    return new ArtifactSyncWorker.Ignored(b);
                }
                return new ArtifactSyncWorker.PerformResync(a.coordinates(), b);
            }
        );
        artifactService.groupTopic()
            .subscribe()
            .atLeastOnce(actorAsk);
        // endregion
    }
}
