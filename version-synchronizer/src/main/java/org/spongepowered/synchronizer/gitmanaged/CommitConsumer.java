package org.spongepowered.synchronizer.gitmanaged;

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
import akka.stream.javadsl.Flow;
import akka.stream.typed.javadsl.ActorFlow;
import io.vavr.collection.List;
import org.spongepowered.downloads.util.akka.ClusterUtil;
import org.spongepowered.downloads.util.akka.FlowUtil;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.api.delegates.CommitDetailsRegistrar;
import org.spongepowered.downloads.versions.api.models.VersionedArtifactUpdates;
import org.spongepowered.synchronizer.gitmanaged.domain.GitCommand;
import org.spongepowered.synchronizer.gitmanaged.domain.GitManagedArtifact;
import org.spongepowered.synchronizer.gitmanaged.util.jgit.CommitResolver;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

public class CommitConsumer {

    public static void setupSubscribers(VersionsService versionsService, ActorContext<?> ctx) {
        final var versionedAssetFlows = CommitConsumer.createVersionedAssetFlows(ctx);
        versionsService.versionedArtifactUpdatesTopic()
            .subscribe()
            .atLeastOnce(versionedAssetFlows);
    }

    public static Flow<VersionedArtifactUpdates, Done, NotUsed> createVersionedAssetFlows(
        ActorContext<?> ctx
    ) {
        final var member = Cluster.get(ctx.getSystem()).selfMember();
        final ActorRef<CommitResolver.Command> workerRef;
        final var uid = UUID.randomUUID();
        final var workerName = "commit-resolver-" + uid;

        if (member.hasRole("commit-resolver")) {
            workerRef = spawnCommitResolver(ctx, uid, workerName);
        } else {
            final var group = Routers.group(CommitResolver.SERVICE_KEY);
            workerRef = ctx.spawn(group, workerName);
        }
        final var sharding = ClusterSharding.get(ctx.getSystem());
        final var associateCommitFlow = registerRawCommitFlow(sharding);

        final var commitExtractedPairNotUsedFlow = getGitRepositoriesFlow(sharding);
        final var resolveCommitDetailsFlow = resolveCommitDetailsFlow(workerRef);
        final var commitResolutionFlow = commitExtractedPairNotUsedFlow.via(resolveCommitDetailsFlow);

        final var registerResolvedCommits = registerResolvedCommitDetails(sharding);

        final var extractedFlow = FlowUtil.broadcast(commitResolutionFlow, associateCommitFlow);

        return FlowUtil.splitClassFlows(
            Pair.create(VersionedArtifactUpdates.CommitExtracted.class, extractedFlow),
            Pair.create(VersionedArtifactUpdates.GitCommitDetailsAssociated.class, registerResolvedCommits)
        );
    }

    private static ActorRef<CommitResolver.Command> spawnCommitResolver(
        ActorContext<?> ctx, UUID uid, String workerName
    ) {
        final ActorRef<CommitResolver.Command> workerRef;

        final var supervised = Behaviors.supervise(
                Routers.group(CommitDetailsRegistrar.SERVICE_KEY)
            )
            .onFailure(SupervisorStrategy.restartWithBackoff(
                Duration.ofMillis(100),
                Duration.ofSeconds(40),
                0.1
            ));
        final var registrar = ctx.spawn(supervised, "commit-details-registrar-" + uid);
        final var resolver = CommitResolver.resolveCommit(registrar);
        final var supervisedResolver = Behaviors.supervise(resolver)
            .onFailure(SupervisorStrategy.restartWithBackoff(
                Duration.ofMillis(100),
                Duration.ofSeconds(40),
                0.1
            ));
        final var pool = Routers.pool(4, supervisedResolver);
        workerRef = ClusterUtil.spawnRemotableWorker(
            ctx, () -> pool,
            () -> CommitResolver.SERVICE_KEY,
            () -> workerName
        );
        return workerRef;
    }

    private static Flow<VersionedArtifactUpdates.CommitExtracted, Done, NotUsed> registerRawCommitFlow(
        ClusterSharding sharding
    ) {
        return Flow.fromFunction(msg -> sharding.entityRefFor(
                    GitManagedArtifact.ENTITY_TYPE_KEY,
                    msg.coordinates().asArtifactCoordinates().asMavenString()
                )
                .<Done>ask(
                    replyTo -> new GitCommand.RegisterRawCommit(msg.coordinates(), msg.commit(), replyTo),
                    Duration.ofSeconds(20)
                )
                .toCompletableFuture()
                .join()
        );
    }

    private static Flow<VersionedArtifactUpdates.CommitExtracted, Pair<VersionedArtifactUpdates.CommitExtracted, List<URI>>, NotUsed> getGitRepositoriesFlow(
        ClusterSharding sharding
    ) {
        return Flow.<VersionedArtifactUpdates.CommitExtracted, Pair<VersionedArtifactUpdates.CommitExtracted, GitCommand.RepositoryResponse>>fromFunction(
            cmd -> sharding
                .entityRefFor(
                    GitManagedArtifact.ENTITY_TYPE_KEY,
                    cmd.coordinates().asArtifactCoordinates().asMavenString()
                )
                .ask(GitCommand.GetRepositories::new, Duration.ofSeconds(20))
                .thenApply(repos -> Pair.create(cmd, repos))
                .toCompletableFuture()
                .join()
        ).map(pair -> Pair.create(pair.first(), pair.second().repositories()));
    }

    private static Flow<Pair<VersionedArtifactUpdates.CommitExtracted, List<URI>>, Done, NotUsed> resolveCommitDetailsFlow(
        ActorRef<CommitResolver.Command> workerRef
    ) {
        return ActorFlow.ask(
            4,
            workerRef,
            Duration.ofMinutes(10),
            (msg, replyTo) -> new CommitResolver.ResolveCommitDetails(
                msg.first().coordinates(), msg.first().commit(), msg.second(), replyTo)
        );
    }

    private static Flow<VersionedArtifactUpdates.GitCommitDetailsAssociated, Done, NotUsed> registerResolvedCommitDetails(
        ClusterSharding sharding
    ) {
        return Flow.fromFunction(event -> sharding
            .entityRefFor(
                GitManagedArtifact.ENTITY_TYPE_KEY,
                event.coordinates().asArtifactCoordinates().asMavenString()
            )
            .<Done>ask(
                replyTo -> new GitCommand.MarkVersionAsResolved(event.coordinates(), event.commit(), replyTo),
                Duration.ofSeconds(20)
            )
            .toCompletableFuture()
            .join()
        );
    }
}
