/*
 * This file is part of SystemOfADownload, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://spongepowered.org/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.downloads.artifact.collection;

import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EffectFactories;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;
import com.lightbend.lagom.javadsl.persistence.AkkaTaggerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.spongepowered.downloads.artifact.api.query.GetVersionsResponse;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

public final class ArtifactCollectionEntity
    extends EventSourcedBehaviorWithEnforcedReplies<ACCommand, ACEvent, ACState> {

    public static final Logger LOGGER = LogManager.getLogger("ArtifactEntity");
    public static final Marker DATA_RETRIEVAL = MarkerManager.getMarker("READ");
    public static EntityTypeKey<ACCommand> ENTITY_TYPE_KEY = EntityTypeKey.create(ACCommand.class, "ArtifactCollection");
    private final String groupId;
    private final Function<ACEvent, Set<String>> tagger;

    public static ArtifactCollectionEntity create(final EntityContext<ACCommand> context) {
        return new ArtifactCollectionEntity(context);
    }

    private ArtifactCollectionEntity(final EntityContext<ACCommand> context) {
        super(
            // PersistenceId needs a typeHint (or namespace) and entityId,
            // we take then from the EntityContext
            PersistenceId.of(
                context.getEntityTypeKey().name(), // <- type hint
                context.getEntityId() // <- business id
            ));
        // we keep a copy of cartI
        this.groupId = context.getEntityId();
        this.tagger = AkkaTaggerAdapter.fromLagom(context, ACEvent.INSTANCE);

    }
    @Override
    public ACState emptyState() {
        return ACState.empty();
    }

    @Override
    public EventHandler<ACState, ACEvent> eventHandler() {
        final var builder = this.newEventHandlerBuilder();
        builder.forAnyState()
            .onEvent(ACEvent.ArtifactGroupUpdated.class, this::updateGroupId)
            .onEvent(ACEvent.ArtifactIdUpdated.class, this::updateArtifactId)
            .onEvent(ACEvent.ArtifactVersionRegistered.class, this::updateVersionRegistered);
        return builder.build();
    }

    @Override
    public CommandHandlerWithReply<ACCommand, ACEvent, ACState> commandHandler() {
        final var builder = this.newCommandHandlerWithReplyBuilder();
        builder.forAnyState()
            .onCommand(ACCommand.RegisterCollection.class, this::handleRegisterCommand)
            .onCommand(ACCommand.GetVersions.class, this::respondToGetVersions);
        return builder.build();
    }

    private ReplyEffect<ACEvent, ACState> handleRegisterCommand(
        final ACState state,
        final ACCommand.RegisterCollection cmd
    ) {
        final var groupNameRegistered = cmd.group.name.toLowerCase(Locale.ROOT);
        final var events = new ArrayList<ACEvent>();
        if (!state.groupId.equals(groupNameRegistered)) {
            events.add(new ACEvent.ArtifactGroupUpdated(cmd.group));
        }
        final String artifactId = cmd.collection.getArtifactId();
        if (!state.artifactId.equals(artifactId)) {
            events.add(new ACEvent.ArtifactIdUpdated(artifactId));
        }
        final String version = cmd.collection.getVersion();
        if (!state.collection.containsKey(version)) {
            events.add(new ACEvent.ArtifactVersionRegistered(version, cmd.collection));
        }
        return this.Effect().persist(events).thenReply(cmd.replyTo, (s) -> NotUsed.notUsed());
    }

    private ACState updateGroupId(
        final ACState state, final ACEvent.ArtifactGroupUpdated event
    ) {
        return new ACState(event.groupId.name.toLowerCase(Locale.ROOT), state.artifactId, state.collection);
    }

    private ACState updateArtifactId(
        final ACState state, final ACEvent.ArtifactIdUpdated event
    ) {
        return new ACState(state.groupId, event.artifactId(), state.collection);
    }

    private ACState updateVersionRegistered(
        final ACState state, final ACEvent.ArtifactVersionRegistered event
    ) {
        return new ACState(
            state.groupId,
            state.artifactId,
            state.collection.put(event.version, event.collection)
        );
    }

    private ReplyEffect<ACEvent, ACState> respondToGetVersions(
        final ACState state,
        final ACCommand.GetVersions cmd
    ) {
        if (!state.artifactId.equals(cmd.artifactId)) {
            return this.Effect().reply(cmd.replyTo, new GetVersionsResponse.ArtifactUnknown(cmd.artifactId));
        }
        return this.Effect().reply(cmd.replyTo, new GetVersionsResponse.VersionsAvailable(state.collection));
    }

}
