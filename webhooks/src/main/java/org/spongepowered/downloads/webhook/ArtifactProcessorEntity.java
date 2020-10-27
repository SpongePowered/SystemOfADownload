package org.spongepowered.downloads.webhook;

import akka.Done;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.git.api.CommitSha;
import org.spongepowered.downloads.webhook.sonatype.Component;

import java.util.Optional;

@SuppressWarnings("unchecked")
public class ArtifactProcessorEntity
    extends PersistentEntity<ArtifactProcessorEntity.Command, ArtifactProcessorEntity.Event, ArtifactProcessorEntity.ProcessingState> {


    public sealed interface Event extends AggregateEvent<Event>, Jsonable {

        AggregateEventTag<Event> TAG = AggregateEventTag.of(Event.class);

        @Override
        default AggregateEventTagger<Event> aggregateTag() {
            return TAG;
        }

        String mavenCoordinates();

        final record InitializeArtifactForProcessing(
            ProcessingState.State newState,
            String mavenCoordinates,
            String componentId) implements Event {
        }

        final record AssociatedMavenMetadata(
            ArtifactCollection collection,
            String mavenCoordinates,
            Map<String, Tuple2<String, String>> artifactPathToSonatypeId
        )
            implements Event {
        }
    }

    public sealed interface Command {

        final record StartProcessing(
            SonatypeWebhookService.SonatypeData webhook,
            ArtifactCollection artifact
        ) implements Command, ReplyType<NotUsed> {
        }

        final record AssociateMetadataWithCollection(
            ArtifactCollection collection,
            Component component
        ) implements Command, ReplyType<NotUsed> {
        }

        final record AssociateCommitShaWithArtifact(
            ArtifactCollection collection,
            CommitSha sha
        ) implements Command, ReplyType<NotUsed> {
        }
    }

    public ArtifactProcessorEntity() {
    }

    @Override
    public Behavior initialBehavior(
        final Optional<ProcessingState> snapshotState
    ) {
        final BehaviorBuilder builder = this.newBehaviorBuilder(ProcessingState.empty());
        builder.setCommandHandler(Command.StartProcessing.class, this::handleStartProcessing);
        builder.setEventHandler(
            Event.InitializeArtifactForProcessing.class, this::initializeFromEvent);

        builder.setCommandHandler(Command.AssociateMetadataWithCollection.class, this::respondToMetadataAssociation);
        builder.setEventHandler(Event.AssociatedMavenMetadata.class, this::associateSonatypeInformation);

        return builder.build();
    }

    private Persist<Event> handleStartProcessing(
        final Command.StartProcessing cmd,
        final CommandContext<NotUsed> ctx
    ) {
        final String mavenCoordinates = cmd.artifact().getMavenCoordinates();
        final String componentId = cmd.webhook().component().componentId();
        if (!this.state().getCoordinates().equals(mavenCoordinates)) {
            ctx.thenPersist(
                new Event.InitializeArtifactForProcessing(
                    ProcessingState.State.EMPTY, mavenCoordinates, componentId),
                message -> ctx.reply(Done.done())
            );
        }
        return ctx.done();
    }

    private ProcessingState initializeFromEvent(
        final Event.InitializeArtifactForProcessing event
    ) {
        return new ProcessingState(event.newState(), event.mavenCoordinates(), HashMap.empty());
    }

    private Persist<Event> respondToMetadataAssociation(
        final Command.AssociateMetadataWithCollection cmd, final CommandContext<NotUsed> ctx
    ) {
        if (this.state().state.hasMetadata()) {
            return ctx.done();
        }
        return ctx.thenPersist(new Event.AssociatedMavenMetadata(
            cmd.collection,
            cmd.collection.getMavenCoordinates(),
            cmd.component.assets().toMap(Component.Asset::path, asset -> new Tuple2<>(asset.id(), asset.downloadUrl()))
        ));
    }

    private ProcessingState associateSonatypeInformation(final Event.AssociatedMavenMetadata event) {
        return new ProcessingState(
            ProcessingState.State.MAVEN_COORDINATES_ASSOCIATED,
            event.mavenCoordinates(),
            event.artifactPathToSonatypeId()
        );
    }

    public static class ProcessingState {

        public enum State {
            EMPTY {
                @Override
                public boolean hasStarted() {
                    return false;
                }

                @Override
                public boolean hasMetadata() {
                    return false;
                }
            },
            MAVEN_COORDINATES_ASSOCIATED {
                @Override
                public boolean hasMetadata() {
                    return false;
                }
            },
            MANIFEST_DOWNLOADED,
            COMMIT_MISSING,
            COMMIT_PRESENT,
            PROCESSED;

            public boolean hasStarted() {
                return true;
            }

            public boolean hasMetadata() {
                return true;
            }
        }

        private final State state;
        private final String coordinates;
        private final Map<String, Tuple2<String, String>> assets;

        public static ProcessingState empty() {
            return new ProcessingState(ProcessingState.State.EMPTY, "", HashMap.empty());
        }

        public ProcessingState(
            final State state, final String coordinates, final Map<String, Tuple2<String, String>> assets
        ) {
            this.state = state;
            this.coordinates = coordinates;
            this.assets = assets;
        }

        public State getState() {
            return this.state;
        }

        public String getCoordinates() {
            return this.coordinates;
        }

        public Map<String, Tuple2<String, String>> getAssetData() {
            return this.assets;
        }
    }
}
