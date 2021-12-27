package org.spongepowered.synchronizer.assetsync;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Routers;
import akka.stream.javadsl.Flow;
import akka.stream.typed.javadsl.ActorFlow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.api.models.ArtifactUpdate;
import org.spongepowered.synchronizer.SonatypeSynchronizer;
import org.spongepowered.synchronizer.SynchronizerSettings;

public class VersionConsumer {
    public static void subscribeToVersionedArtifactUpdates(
        final VersionsService versionsService, final ObjectMapper mapper,
        final ActorContext<SonatypeSynchronizer.Command> context,
        final SynchronizerSettings settings
    ) {
        // region Synchronize Versioned Assets through Sonatype Search
        final var componentPool = Routers.pool(
            settings.asset.poolSize,
            Behaviors.supervise(VersionedComponentWorker.gatherComponents(versionsService, mapper))
                .onFailure(settings.asset.backoff)
        );
        final var componentRef = context.spawn(
            componentPool,
            "version-component-registration",
            DispatcherSelector.defaultDispatcher()
        );
        final Flow<ArtifactUpdate, Done, NotUsed> versionedFlow = ActorFlow.ask(
            settings.asset.parallelism,
            componentRef,
            settings.asset.timeout,
            (g, b) -> {
                if (!(g instanceof ArtifactUpdate.ArtifactVersionRegistered a)) {
                    return new VersionedComponentWorker.Ignored(b);
                }
                return new VersionedComponentWorker.GatherComponentsForArtifact(a.coordinates(), b);
            }
        );
        versionsService.artifactUpdateTopic()
            .subscribe()
            .atLeastOnce(versionedFlow);
        // endregion
    }
}
