package org.spongepowered.downloads.webhook.worker;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import io.vavr.Tuple2;
import io.vavr.collection.Map;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.git.api.CommitSha;
import org.spongepowered.downloads.webhook.ScrapedArtifactEvent;
import org.spongepowered.downloads.webhook.sonatype.Component;

import java.util.Optional;
import java.util.StringJoiner;

@SuppressWarnings("unchecked")
public class ScrapedArtifactEntity extends PersistentEntity<ScrapedArtifactEntity.Command, ScrapedArtifactEvent, ScrapedArtifactEntity.ProcessingState> {

    @Override
    public Behavior initialBehavior(
        final Optional<ProcessingState> snapshotState
    ) {
        final BehaviorBuilder builder = this.newBehaviorBuilder(snapshotState.orElseGet(ProcessingState.EmptyState::new));

        builder.setCommandHandler(Command.RequestArtifactForProcessing.class, this::respondRequestArtifactForProcessing);
        builder.setCommandHandler(Command.AssociateCommitShaWithArtifact.class, this::respondToAssociatingCommitShaWithArtifact);
        return builder.build();
    }

    static sealed interface Command {
        final record AssociateMetadataWithCollection(
            ArtifactCollection collection,
            Component component,
            String tagVersion
        ) implements Command, ReplyType<NotUsed> {
        }


        final record RequestArtifactForProcessing(String groupId, String artifactId, String requested) implements Command, ReplyType<NotUsed> {
        }
        final record AssociateCommitShaWithArtifact(
            ArtifactCollection collection,
            CommitSha sha
        ) implements Command, ReplyType<NotUsed> {
        }
    }

    sealed interface ProcessingState {

        boolean hasStarted();

        boolean hasMetadata();

        boolean hasCommit();

        boolean hasCompleted();

        Optional<String> getCoordinates();

        Optional<String> getRepository();

        default Optional<String> getArtifactId() {
            return this.getCoordinates().map(coords -> coords.split(":")[1]);
        }

        default Optional<String> getGroupId() {
            return this.getCoordinates().map(coords -> coords.split(":")[0]);
        }

        default Optional<String> getMavenVersion() {
            return this.getCoordinates().map(coords -> coords.split(":")[2]);
        }

        Optional<Map<String, Tuple2<String, String>>> getArtifacts();

        static final record EmptyState() implements ProcessingState {

            public static EmptyState empty() {
                return new EmptyState();
            }

            @Override
            public boolean hasStarted() {
                return false;
            }

            @Override
            public boolean hasMetadata() {
                return false;
            }

            @Override
            public boolean hasCommit() {
                return false;
            }

            @Override
            public boolean hasCompleted() {
                return false;
            }

            @Override
            public Optional<String> getCoordinates() {
                return Optional.empty();
            }

            @Override
            public Optional<String> getRepository() {
                return Optional.empty();
            }

            @Override
            public Optional<Map<String, Tuple2<String, String>>> getArtifacts() {
                return Optional.empty();
            }
        }

        static final record MetadataState(
            String coordinates,
            String repository,
            Map<String, Tuple2<String, String>> artifacts
        ) implements ProcessingState {

            @Override
            public boolean hasStarted() {
                return true;
            }

            @Override
            public boolean hasMetadata() {
                return true;
            }

            @Override
            public boolean hasCommit() {
                return false;
            }

            @Override
            public boolean hasCompleted() {
                return false;
            }

            @Override
            public Optional<String> getRepository() {
                return Optional.of(this.repository());
            }

            @Override
            public Optional<String> getCoordinates() {
                return Optional.of(this.coordinates);
            }

            @Override
            public Optional<Map<String, Tuple2<String, String>>> getArtifacts() {
                return Optional.of(this.artifacts);
            }
        }

        static final record CommittedState(String s, String repository, Map<String, Tuple2<String, String>> artifacts, CommitSha commit)
            implements ProcessingState {
            @Override
            public boolean hasStarted() {
                return true;
            }

            @Override
            public boolean hasMetadata() {
                return true;
            }

            @Override
            public boolean hasCommit() {
                return true;
            }

            @Override
            public boolean hasCompleted() {
                return false;
            }

            @Override
            public Optional<String> getCoordinates() {
                return Optional.empty();
            }

            @Override
            public Optional<String> getRepository() {
                return Optional.of(this.repository());
            }

            @Override
            public Optional<Map<String, Tuple2<String, String>>> getArtifacts() {
                return Optional.of(this.artifacts);
            }
        }

    }


    private Persist<ScrapedArtifactEvent> respondToAssociatingCommitShaWithArtifact(
        final Command.AssociateCommitShaWithArtifact cmd,
        final CommandContext<NotUsed> ctx) {
        if (!this.state().hasCommit()) {
            ctx.thenPersist(new ScrapedArtifactEvent.AssociateCommitSha(
                cmd.collection,
                cmd.collection.getMavenCoordinates(),
                cmd.collection.getGroup().getGroupCoordinates(),
                cmd.collection.getArtifactId(),
                cmd.collection.getVersion(),
                cmd.sha
            ));
            return ctx.done();
        }
        return ctx.done();
    }

    private Persist<ScrapedArtifactEvent> respondRequestArtifactForProcessing(
        final Command.RequestArtifactForProcessing cmd,
        final CommandContext<NotUsed> ctx
    ) {
        final String mavenCoordinates = new StringJoiner(":").add(cmd.groupId).add(cmd.artifactId).add(cmd.requested).toString();

        if (this.state().getCoordinates().map(coords -> !coords.equals(mavenCoordinates)).orElse(true)) {
            ctx.thenPersist(
                new ScrapedArtifactEvent.ArtifactRequested(cmd.groupId, cmd.artifactId, cmd.requested, mavenCoordinates),
                message -> ctx.reply(NotUsed.notUsed())
            );
        }
        return ctx.done();
    }
}
