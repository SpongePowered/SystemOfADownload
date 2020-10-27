package org.spongepowered.downloads.webhook;

import akka.Done;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;

import java.util.Optional;

@SuppressWarnings("unchecked")
public class ArtifactProcessorEntity extends PersistentEntity<ArtifactProcessingCommand, ArtifactProcessingEvent, ArtifactProcessingState> {

    public ArtifactProcessorEntity() { }

    @Override
    public Behavior initialBehavior(
        final Optional<ArtifactProcessingState> snapshotState
    ) {
        final BehaviorBuilder builder = this.newBehaviorBuilder(ArtifactProcessingState.empty());
        builder.setCommandHandler(ArtifactProcessingCommand.StartProcessing.class, this::handleStartProcessing);
        builder.setEventHandler(ArtifactProcessingEvent.InitializeArtifactForProcessing.class, this::initializeFromEvent);

        return builder.build();
    }

    private Persist<ArtifactProcessingEvent> handleStartProcessing(
        final ArtifactProcessingCommand.StartProcessing cmd,
        final CommandContext<NotUsed> ctx
    ) {
        final String mavenCoordinates = cmd.artifact().getMavenCoordinates();
        final String componentId = cmd.webhook().component().componentId();
        if (!this.state().getCoordinates().equals(mavenCoordinates)) {
            ctx.thenPersist(
                new ArtifactProcessingEvent.InitializeArtifactForProcessing(ArtifactProcessingState.State.EMPTY, mavenCoordinates, componentId),
                message -> ctx.reply(Done.done())
            );
        }
        return ctx.done();
    }

    private ArtifactProcessingState initializeFromEvent(final ArtifactProcessingEvent.InitializeArtifactForProcessing event) {
        return new ArtifactProcessingState(event.newState(), event.mavenCoordinates(), event.componentId());
    }

}
