package org.spongepowered.downloads.artifact.details;

import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import com.lightbend.lagom.javadsl.persistence.AkkaTaggerAdapter;
import org.spongepowered.downloads.artifact.group.GroupCommand;
import org.spongepowered.downloads.artifact.group.GroupEvent;
import org.spongepowered.downloads.artifact.group.GroupState;

import java.util.Set;
import java.util.function.Function;

public class ArtifactDetailsEntity  extends EventSourcedBehaviorWithEnforcedReplies<DetailsCommand, DetailsEvent, DetailsState> {
    public static EntityTypeKey<DetailsCommand> ENTITY_TYPE_KEY = EntityTypeKey.create(DetailsCommand.class, "DetailsEntity");


    private ArtifactDetailsEntity(EntityContext<DetailsCommand> context) {
        super(
            PersistenceId.of(
                context.getEntityTypeKey().name(), // <- type hint
                context.getEntityId() // <- business id
            ));

    }
    @Override
    public DetailsState emptyState() {
        return new DetailsState();
    }

    @Override
    public EventHandler<DetailsState, DetailsEvent> eventHandler() {
        return null;
    }

    @Override
    public CommandHandlerWithReply<DetailsCommand, DetailsEvent, DetailsState> commandHandler() {
        return null;
    }
}
