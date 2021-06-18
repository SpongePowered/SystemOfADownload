package org.spongepowered.downloads.artifact.details;

import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import org.spongepowered.downloads.artifact.api.query.GetArtifactDetailsResponse;
import org.spongepowered.downloads.artifact.details.state.DetailsState;
import org.spongepowered.downloads.artifact.details.state.EmptyState;
import org.spongepowered.downloads.artifact.details.state.PopulatedState;

import java.util.List;

public class ArtifactDetailsEntity
    extends EventSourcedBehaviorWithEnforcedReplies<DetailsCommand, DetailsEvent, DetailsState> {
    public static EntityTypeKey<DetailsCommand> ENTITY_TYPE_KEY = EntityTypeKey.create(
        DetailsCommand.class, "DetailsEntity");


    private ArtifactDetailsEntity(EntityContext<DetailsCommand> context) {
        super(
            PersistenceId.of(
                context.getEntityTypeKey().name(),
                context.getEntityId()
            ));

    }

    public static ArtifactDetailsEntity create(EntityContext<DetailsCommand> context) {
        return new ArtifactDetailsEntity(context);
    }

    @Override
    public DetailsState emptyState() {
        return new EmptyState();
    }

    @Override
    public EventHandler<DetailsState, DetailsEvent> eventHandler() {
        final var builder = this.newEventHandlerBuilder();

        builder.forAnyState()
            .onEvent(
                DetailsEvent.ArtifactRegistered.class,
                (state, event) -> new PopulatedState(event.coordinates, state.displayName(), state.website(), state.gitRepository(), state.issues())
            )
            .onEvent(DetailsEvent.ArtifactDetailsUpdated.class,
                (state, event) -> new PopulatedState(event.coordinates, event.displayName, state.website(), state.gitRepository(), state.issues())
            );

        return builder.build();
    }

    @Override
    public CommandHandlerWithReply<DetailsCommand, DetailsEvent, DetailsState> commandHandler() {
        final var builder = this.newCommandHandlerWithReplyBuilder();

        builder.forNullState()
            .onCommand(
                DetailsCommand.GetArtifactDetails.class,
                (cmd) -> this.Effect()
                    .reply(cmd.replyTo, new GetArtifactDetailsResponse.ArtifactMissing(cmd.artifactId))
            );
        builder.forStateType(EmptyState.class)
            .onCommand(
                DetailsCommand.GetArtifactDetails.class,
                (cmd) -> this.Effect()
                    .reply(cmd.replyTo, new GetArtifactDetailsResponse.ArtifactMissing(cmd.artifactId))
            )
            .onCommand(
                DetailsCommand.RegisterArtifact.class,
                (cmd) -> this.Effect()
                    .persist(List.of(new DetailsEvent.ArtifactRegistered(cmd.coordinates), new DetailsEvent.ArtifactDetailsUpdated(cmd.coordinates, cmd.displayName)))
                    .thenReply(cmd.replyTo, (state) -> NotUsed.notUsed())
            );

        builder.forStateType(PopulatedState.class)
            .onCommand(DetailsCommand.RegisterArtifact.class, (s, cmd) -> this.Effect().reply(cmd.replyTo, NotUsed.notUsed()))
            .onCommand(
                DetailsCommand.GetArtifactDetails.class,
                (s, cmd) -> this.Effect().reply(
                    cmd.replyTo,
                    new GetArtifactDetailsResponse.RetrievedArtifact(
                        s.coordinates(),
                        s.displayName(),
                        s.website(),
                        s.gitRepository(),
                        s.issues()
                    )
                )
            );

        return builder.build();
    }
}
