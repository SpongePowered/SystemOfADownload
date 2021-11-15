package org.spongepowered.downloads.versions.worker.actor.artifacts;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.ServiceKey;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorFlow;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.worker.actor.delegates.RawCommitReceiver;

import java.time.Duration;
import java.util.UUID;

public final class FileCollectionOperator {

    public static final ServiceKey<Request> KEY = ServiceKey.create(Request.class, "file-collection-operator");

    public sealed interface Request {}

    public static final record TryFindingCommitForFiles(
        List<PotentiallyUsableAsset> files,
        MavenCoordinates coordinates
    ) implements Request {}

    public static Behavior<Request> scanJarFilesForCommit(ActorRef<CommitExtractor.ChildCommand> commitExtractor) {
        return Behaviors.setup(ctx -> {
            final var uid = UUID.randomUUID();
            final var receiver = Behaviors.supervise(RawCommitReceiver.receive())
                .onFailure(SupervisorStrategy.restart());
            final var receiverRef = ctx.spawn(receiver, "file-scan-result-receiver-" + uid);
            return Behaviors.receive(Request.class)
                .onMessage(TryFindingCommitForFiles.class, msg -> {
                    final List<PotentiallyUsableAsset> files = msg.files();
                    final var from = Source.from(files);
                    final var extraction = ActorFlow.ask(
                        4,
                        commitExtractor,
                        Duration.ofMinutes(20),
                        CommitExtractor.AttemptFileCommit::new
                    );
                    final var receiverSink = Sink.foreach(receiverRef::tell);
                    final var herp = extraction.to(receiverSink).named("herp");
                    final var to = from.to(herp);
                    to.run(ctx.getSystem().classicSystem());
                    return Behaviors.same();
                })
                .build();
        });
    }
}
