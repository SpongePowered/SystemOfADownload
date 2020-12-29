package org.spongepowered.downloads.webhook;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.git.api.CommitSha;
import org.spongepowered.downloads.webhook.sonatype.Component;
import org.spongepowered.downloads.webhook.sonatype.SonatypeClient;

import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

@SuppressWarnings("unchecked")
public class ArtifactProcessorEntity
    extends PersistentEntity<ArtifactProcessorEntity.Command, ScrapedArtifactEvent, ProcessingState> {


    public interface Command {

        final static class StartProcessing implements Command, ReplyType<NotUsed> {
            private final SonatypeData webhook;
            private final ArtifactCollection artifact;

            public StartProcessing(
                final SonatypeData webhook,
                final ArtifactCollection artifact
            ) {
                this.webhook = webhook;
                this.artifact = artifact;
            }

            public SonatypeData webhook() {
                return this.webhook;
            }

            public ArtifactCollection artifact() {
                return this.artifact;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (StartProcessing) obj;
                return Objects.equals(this.webhook, that.webhook) &&
                    Objects.equals(this.artifact, that.artifact);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.webhook, this.artifact);
            }

            @Override
            public String toString() {
                return "StartProcessing[" +
                    "webhook=" + this.webhook + ", " +
                    "artifact=" + this.artifact + ']';
            }

        }

        final static class AssociateMetadataWithCollection implements Command, ReplyType<NotUsed> {
            private final ArtifactCollection collection;
            private final Component component;
            private final String tagVersion;

            public AssociateMetadataWithCollection(
                final ArtifactCollection collection,
                final Component component,
                final String tagVersion
            ) {
                this.collection = collection;
                this.component = component;
                this.tagVersion = tagVersion;
            }

            public ArtifactCollection collection() {
                return this.collection;
            }

            public Component component() {
                return this.component;
            }

            public String tagVersion() {
                return this.tagVersion;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (AssociateMetadataWithCollection) obj;
                return Objects.equals(this.collection, that.collection) &&
                    Objects.equals(this.component, that.component) &&
                    Objects.equals(this.tagVersion, that.tagVersion);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.collection, this.component, this.tagVersion);
            }

            @Override
            public String toString() {
                return "AssociateMetadataWithCollection[" +
                    "collection=" + this.collection + ", " +
                    "component=" + this.component + ", " +
                    "tagVersion=" + this.tagVersion + ']';
            }

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

        builder.setEventHandler(ScrapedArtifactEvent.AssociateCommitSha.class, this::handleCommitShaAssociation);
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
