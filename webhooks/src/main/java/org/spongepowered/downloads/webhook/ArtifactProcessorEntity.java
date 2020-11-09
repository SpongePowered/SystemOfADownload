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
    extends PersistentEntity<ArtifactProcessorEntity.Command, ArtifactProcessorEntity.Event, ProcessingState> {


    public sealed interface Event extends AggregateEvent<Event>, Jsonable {

        AggregateEventTag<Event> TAG = AggregateEventTag.of(Event.class);

        @Override
        default AggregateEventTagger<Event> aggregateTag() {
            return TAG;
        }

        String mavenCoordinates();

        final record InitializeArtifactForProcessing(
            String mavenCoordinates,
            String repository,
            String componentId
        ) implements Event {
        }

        final record AssociatedMavenMetadata(
            ArtifactCollection collection,
            String mavenCoordinates,
            String tagVersion, Map<String, Tuple2<String, String>> artifactPathToSonatypeId
        )
            implements Event {
        }

        final record AssociateCommitSha(
            ArtifactCollection collection,
            String mavenCoordinates,
            String groupId,
            String artifactId,
            String version,
            CommitSha commit
        ) implements Event {

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
            Component component,
            String tagVersion
        ) implements Command, ReplyType<NotUsed> {
        }

        final record AssociateCommitShaWithArtifact(
            ArtifactCollection collection,
            CommitSha sha
        ) implements Command, ReplyType<NotUsed> {
        }

        final record RequestArtifactForProcessing(String requested) implements Command, ReplyType<NotUsed> {
        }
    }

    public ArtifactProcessorEntity() {
    }

    @Override
    public Behavior initialBehavior(
        final Optional<ProcessingState> snapshotState
    ) {
        final BehaviorBuilder builder = this.newBehaviorBuilder(ProcessingState.EmptyState.empty());
        builder.setCommandHandler(Command.StartProcessing.class, this::handleStartProcessing);
        builder.setEventHandler(
            Event.InitializeArtifactForProcessing.class, this::initializeFromEvent);

        builder.setCommandHandler(Command.AssociateMetadataWithCollection.class, this::respondToMetadataAssociation);
        builder.setEventHandler(Event.AssociatedMavenMetadata.class, this::associateSonatypeInformation);

        builder.setCommandHandler(Command.AssociateCommitShaWithArtifact.class, this::respondToAssociatingCommitShaWithArtifact);
        builder.setEventHandler(Event.AssociateCommitSha.class, this::handleCommitShaAssociation);
        return builder.build();
    }

    private Persist<Event> handleStartProcessing(
        final Command.StartProcessing cmd,
        final CommandContext<NotUsed> ctx
    ) {
        final String mavenCoordinates = cmd.artifact().getMavenCoordinates();
        final String componentId = cmd.webhook().component().componentId();

        if (this.state().getCoordinates().map(coords -> !coords.equals(mavenCoordinates)).orElse(false)) {
            ctx.thenPersist(
                new Event.InitializeArtifactForProcessing(mavenCoordinates, cmd.webhook().repositoryName(), componentId),
                message -> ctx.reply(Done.done())
            );
        }
        return ctx.done();
    }

    private ProcessingState initializeFromEvent(
        final Event.InitializeArtifactForProcessing event
    ) {
        return new ProcessingState.MetadataState(event.mavenCoordinates(), event.repository(), HashMap.empty());
    }

    private Persist<Event> respondToMetadataAssociation(
        final Command.AssociateMetadataWithCollection cmd, final CommandContext<NotUsed> ctx
    ) {
        if (this.state().hasMetadata()) {
            return ctx.done();
        }
        return ctx.thenPersist(new Event.AssociatedMavenMetadata(
            cmd.collection,
            cmd.collection.getMavenCoordinates(),
            cmd.tagVersion,
            cmd.component.assets().toMap(Component.Asset::path, asset -> new Tuple2<>(asset.id(), asset.downloadUrl()))
        ));
    }

    private ProcessingState associateSonatypeInformation(final Event.AssociatedMavenMetadata event) {
        return new ProcessingState.MetadataState(
            event.mavenCoordinates(),
            this.state().getRepository().get(),
            event.artifactPathToSonatypeId()
        );
    }

    private Persist<Event> respondToAssociatingCommitShaWithArtifact(
        final Command.AssociateCommitShaWithArtifact cmd,
        final CommandContext<NotUsed> ctx) {
        // TODO - build in some better state control
        if (!this.state().hasCommit()) {
            ctx.thenPersist(new Event.AssociateCommitSha(
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

    private ProcessingState handleCommitShaAssociation(final Event.AssociateCommitSha event) {
        if (this.state().hasCommit()) {
            return this.state();
        }
        return new ProcessingState.CommittedState(
            event.mavenCoordinates(),
            this.state().getRepository().get(),
            this.state().getArtifacts().get(),
            event.commit
        );
    }

}
