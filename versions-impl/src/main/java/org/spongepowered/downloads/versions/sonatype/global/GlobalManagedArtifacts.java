package org.spongepowered.downloads.versions.sonatype.global;

import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;
import com.lightbend.lagom.javadsl.persistence.AkkaTaggerAdapter;
import io.vavr.collection.List;

import java.util.Set;
import java.util.function.Function;

public class GlobalManagedArtifacts
    extends EventSourcedBehaviorWithEnforcedReplies<ManageCommand, GloballyManagedArtifactEvent, ManagedArtifacts> {
    public static EntityTypeKey<ManageCommand> ENTITY_TYPE_KEY = EntityTypeKey.create(
        ManageCommand.class, "GlobalManagedArtifacts");
    private final String groupId;
    private final Function<GloballyManagedArtifactEvent, Set<String>> tagger;

    public static GlobalManagedArtifacts create(final EntityContext<ManageCommand> context) {
        return new GlobalManagedArtifacts(context);
    }

    private GlobalManagedArtifacts(final EntityContext<ManageCommand> context) {
        super(
            // PersistenceId needs a typeHint (or namespace) and entityId,
            // we take then from the EntityContext
            PersistenceId.of(
                context.getEntityTypeKey().name(), // <- type hint
                context.getEntityId() // <- business id
            ));
        // we keep a copy of cartI
        this.groupId = context.getEntityId();
        this.tagger = AkkaTaggerAdapter.fromLagom(context, GloballyManagedArtifactEvent.INSTANCE);

    }

    @Override
    public ManagedArtifacts emptyState() {
        return new ManagedArtifacts(List.empty());
    }

    @Override
    public EventHandler<ManagedArtifacts, GloballyManagedArtifactEvent> eventHandler() {
        final var builder = this.newEventHandlerBuilder();

        builder.forAnyState().onEvent(
            GloballyManagedArtifactEvent.Registered.class, (s, e) -> s.withArtifact(e.coordinates));
        return builder.build();
    }

    @Override
    public CommandHandlerWithReply<ManageCommand, GloballyManagedArtifactEvent, ManagedArtifacts> commandHandler() {
        final var builder = this.newCommandHandlerWithReplyBuilder();
        builder.forAnyState()
            .onCommand(ManageCommand.Add.class, this::handleAdd)
            .onCommand(ManageCommand.GetAllArtifacts.class, (s, cmd) -> this.Effect().reply(cmd.replyTo, s.artifacts));
        return builder.build();
    }

    private ReplyEffect<GloballyManagedArtifactEvent, ManagedArtifacts> handleAdd(
        final ManagedArtifacts state, final
    ManageCommand.Add cmd
    ) {
        if (state.artifacts.contains(cmd.coordinates)) {
            return this.Effect().reply(cmd.replyTo, NotUsed.notUsed());
        }
        return this.Effect().persist(new GloballyManagedArtifactEvent.Registered(cmd.coordinates))
            .thenReply(cmd.replyTo, (s) -> NotUsed.notUsed());
    }

    @Override
    public Set<String> tagsFor(final GloballyManagedArtifactEvent acEvent) {
        return this.tagger.apply(acEvent);
    }
}
