package org.spongepowered.downloads.artifact.global;

import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;
import akka.persistence.typed.javadsl.RetentionCriteria;
import com.lightbend.lagom.javadsl.persistence.AkkaTaggerAdapter;
import org.spongepowered.downloads.artifact.api.query.GroupsResponse;

import java.util.Set;
import java.util.function.Function;

public class GlobalRegistration
    extends EventSourcedBehaviorWithEnforcedReplies<GlobalCommand, GlobalEvent, GlobalState> {

    public static EntityTypeKey<GlobalCommand> ENTITY_TYPE_KEY = EntityTypeKey.create(
        GlobalCommand.class, "GlobalEntity");
    private final String groupId;
    private final Function<GlobalEvent, Set<String>> tagger;

    private GlobalRegistration(EntityContext<GlobalCommand> context) {
        super(
            // PersistenceId needs a typeHint (or namespace) and entityId,
            // we take then from the EntityContext
            PersistenceId.of(
                context.getEntityTypeKey().name(), // <- type hint
                context.getEntityId() // <- business id
            ));
        // we keep a copy of cartI
        this.groupId = context.getEntityId();
        this.tagger = AkkaTaggerAdapter.fromLagom(context, GlobalEvent.TAG);

    }

    public static GlobalRegistration create(EntityContext<GlobalCommand> context) {
        return new GlobalRegistration(context);
    }

    @Override
    public GlobalState emptyState() {
        return new GlobalState();
    }

    @Override
    public EventHandler<GlobalState, GlobalEvent> eventHandler() {
        final var builder = this.newEventHandlerBuilder();
        builder.forAnyState()
            .onEvent(GlobalEvent.GroupRegistered.class, (state, event) -> {
                return new GlobalState(state.groups.append(event.group));
            });
        return builder.build();
    }

    @Override
    public RetentionCriteria retentionCriteria() {
        return RetentionCriteria.snapshotEvery(10, 10);
    }

    @Override
    public CommandHandlerWithReply<GlobalCommand, GlobalEvent, GlobalState> commandHandler() {
        final var builder = this.newCommandHandlerWithReplyBuilder();
        builder.forAnyState()
            .onCommand(
                GlobalCommand.GetGroups.class,
                (state, cmd) -> this.Effect().reply(cmd.replyTo, new GroupsResponse.Available(state.groups))
            )
            .onCommand(
                GlobalCommand.RegisterGroup.class,
                this::handleRegisterGroup
            );
        return builder.build();
    }

    private ReplyEffect<GlobalEvent, GlobalState> handleRegisterGroup(GlobalState state, GlobalCommand.RegisterGroup cmd) {
        if (!state.groups.contains(cmd.group)) {
            return this.Effect().persist(new GlobalEvent.GroupRegistered(cmd.group)).thenReply(cmd.replyTo, (s) -> NotUsed.notUsed());
        }
        return this.Effect().reply(cmd.replyTo, NotUsed.notUsed());
    }

}
