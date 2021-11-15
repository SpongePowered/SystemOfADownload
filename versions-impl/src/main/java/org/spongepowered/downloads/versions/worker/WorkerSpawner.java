package org.spongepowered.downloads.versions.worker;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.cluster.Member;
import org.spongepowered.downloads.versions.worker.actor.artifacts.CommitExtractor;
import org.spongepowered.downloads.versions.worker.actor.artifacts.FileCollectionOperator;

import java.util.UUID;
import java.util.function.Supplier;

public final class WorkerSpawner {

    public static void spawnWorkers(ActorSystem<Void> system, Member member, ActorContext<Void> ctx) {
        // Set up the usual actors
        final var versionConfig = VersionExtension.Settings.get(system);
        final var poolSizePerInstance = versionConfig.commitFetch.poolSize;

        for (int i = 0; i < poolSizePerInstance; i++) {
            final var commitFetcherUID = UUID.randomUUID();
            if (member.hasRole("file-extractor")) {
                final ActorRef<CommitExtractor.ChildCommand> commitExtractor = WorkerSpawner.spawnRemotableWorker(
                    ctx,
                    CommitExtractor::extractCommitFromAssets,
                    () -> CommitExtractor.SERVICE_KEY,
                    () -> "file-commit-worker-" + commitFetcherUID
                );
                WorkerSpawner.spawnRemotableWorker(
                    ctx,
                    () -> FileCollectionOperator.scanJarFilesForCommit(commitExtractor),
                    () -> FileCollectionOperator.KEY,
                    () -> "file-collection-worker-" + commitFetcherUID
                );
            }
        }
    }

    public static <A> ActorRef<A> spawnRemotableWorker(
        ActorContext<Void> ctx, Supplier<Behavior<A>> behaviorSupplier, Supplier<ServiceKey<A>> keyProvider,
        Supplier<String> workerName
    ) {
        final var behavior = behaviorSupplier.get();
        final var assetRefresher = Behaviors.supervise(behavior)
            .onFailure(SupervisorStrategy.resume());
        final var name = workerName.get();

        final var workerRef = ctx.spawn(
            assetRefresher,
            name,
            DispatcherSelector.defaultDispatcher()
        );
        // Announce it to the cluster
        ctx.getSystem().receptionist().tell(Receptionist.register(keyProvider.get(), workerRef));
        return workerRef;
    }


}
