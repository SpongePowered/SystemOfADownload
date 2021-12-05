package org.spongepowered.downloads.util.akka;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;

import java.util.function.Supplier;

public class ClusterUtil {
    public static <A> ActorRef<A> spawnRemotableWorker(
        ActorContext<?> ctx, Supplier<Behavior<A>> behaviorSupplier, Supplier<ServiceKey<A>> keyProvider,
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
