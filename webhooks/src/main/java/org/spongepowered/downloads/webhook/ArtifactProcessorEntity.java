package org.spongepowered.downloads.webhook;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.git.api.CommitSha;
import org.spongepowered.downloads.webhook.sonatype.Component;
import org.spongepowered.downloads.webhook.sonatype.SonatypeClient;

import java.util.Optional;
import java.util.StringJoiner;

@SuppressWarnings("unchecked")
public class ArtifactProcessorEntity
    extends PersistentEntity<ArtifactProcessorEntity.Command, ScrapedArtifactEvent, ProcessingState> {


    public sealed interface Command {

        final record StartProcessing(
            SonatypeData webhook,
            ArtifactCollection artifact
        ) implements Command, ReplyType<NotUsed> {
        }

        final record AssociateMetadataWithCollection(
            ArtifactCollection collection,
            Component component,
            String tagVersion
        ) implements Command, ReplyType<NotUsed> {
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
        builder.setEventHandler(ScrapedArtifactEvent.InitializeArtifactForProcessing.class, this::initializeFromEvent);

        builder.setCommandHandler(Command.AssociateMetadataWithCollection.class, this::respondToMetadataAssociation);
        builder.setEventHandler(ScrapedArtifactEvent.AssociatedMavenMetadata.class, this::associateSonatypeInformation);

        builder.setCommandHandler(Command.AssociateCommitShaWithArtifact.class, this::respondToAssociatingCommitShaWithArtifact);
        builder.setEventHandler(ScrapedArtifactEvent.AssociateCommitSha.class, this::handleCommitShaAssociation);

        builder.setCommandHandler(Command.RequestArtifactForProcessing.class, this::respondRequestArtifactForProcessing);
        builder.setEventHandler(ScrapedArtifactEvent.ArtifactRequested.class, this::handleArtifactRequested);
        return builder.build();
    }

    private Persist<ScrapedArtifactEvent> handleStartProcessing(
        final Command.StartProcessing cmd,
        final CommandContext<NotUsed> ctx
    ) {
        final String mavenCoordinates = cmd.artifact().getMavenCoordinates();
        final String componentId = cmd.webhook().component().componentId();

        if (this.state().getCoordinates().map(coords -> !coords.equals(mavenCoordinates)).orElse(false)) {
            ctx.thenPersist(
                new ScrapedArtifactEvent.InitializeArtifactForProcessing(mavenCoordinates, cmd.webhook().repositoryName(), componentId),
                message -> ctx.reply(NotUsed.notUsed())
            );
        }
        return ctx.done();
    }

    private ProcessingState initializeFromEvent(
        final ScrapedArtifactEvent.InitializeArtifactForProcessing event
    ) {
        return new ProcessingState.MetadataState(event.mavenCoordinates(), event.repository(), HashMap.empty());
    }

    private Persist<ScrapedArtifactEvent> respondToMetadataAssociation(
        final Command.AssociateMetadataWithCollection cmd, final CommandContext<NotUsed> ctx
    ) {
        if (this.state().hasMetadata()) {
            return ctx.done();
        }
        return ctx.thenPersist(new ScrapedArtifactEvent.AssociatedMavenMetadata(
            cmd.collection,
            cmd.collection.getMavenCoordinates(),
            cmd.tagVersion,
            cmd.component.assets().toMap(Component.Asset::path, asset -> new Tuple2<>(asset.id(), asset.downloadUrl()))
        ));
    }

    private ProcessingState associateSonatypeInformation(final ScrapedArtifactEvent.AssociatedMavenMetadata event) {
        return new ProcessingState.MetadataState(
            event.mavenCoordinates(),
            this.state().getRepository().get(),
            event.artifactPathToSonatypeId()
        );
    }

    private Persist<ScrapedArtifactEvent> respondToAssociatingCommitShaWithArtifact(
        final Command.AssociateCommitShaWithArtifact cmd,
        final CommandContext<NotUsed> ctx) {
        // TODO - build in some better state control
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

    private ProcessingState handleArtifactRequested(final ScrapedArtifactEvent.ArtifactRequested event) {
        if (this.state().getCoordinates().map(coords -> !coords.equals(event.mavenCoordinates())).orElse(true)) {
            return new ProcessingState.MetadataState(event.mavenCoordinates(), SonatypeClient.getConfig().getPublicRepo(), HashMap.empty());
        }
        return this.state();
    }

    private ProcessingState handleCommitShaAssociation(final ScrapedArtifactEvent.AssociateCommitSha event) {
        if (this.state().hasCommit()) {
            return this.state();
        }
        return new ProcessingState.CommittedState(
            event.mavenCoordinates(),
            this.state().getRepository().get(),
            this.state().getArtifacts().get(),
            event.commit()
        );
    }

}
