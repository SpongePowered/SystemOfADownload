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
package org.spongepowered.downloads.versions.worker.actor.global;

import akka.Done;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;
import io.vavr.collection.List;

public class GlobalRegistration
    extends EventSourcedBehaviorWithEnforcedReplies<GlobalCommand, GlobalEvent, GlobalState> {

    public static EntityTypeKey<GlobalCommand> ENTITY_TYPE_KEY = EntityTypeKey.create(
        GlobalCommand.class, "GlobalArtifactEntity");
    private final String groupId;
    private final ActorContext<GlobalCommand> ctx;

    private GlobalRegistration(ActorContext<GlobalCommand> ctx, String entityId, PersistenceId persistenceId) {
        super(persistenceId);
        this.ctx = ctx;
        this.groupId = entityId;
    }

    public static Behavior<GlobalCommand> create(String entityId, PersistenceId persistenceId) {
        return Behaviors.setup(ctx -> new GlobalRegistration(ctx, entityId, persistenceId));
    }

    @Override
    public GlobalState emptyState() {
        return new GlobalState(List.empty());
    }

    @Override
    public EventHandler<GlobalState, GlobalEvent> eventHandler() {
        final var builder = this.newEventHandlerBuilder();
        builder.forAnyState()
            .onEvent(
                GlobalEvent.ArtifactRegistered.class,
                (state, event) -> new GlobalState(state.artifacts().append(event.coordinates()))
            );
        return builder.build();
    }

    @Override
    public CommandHandlerWithReply<GlobalCommand, GlobalEvent, GlobalState> commandHandler() {
        final var builder = this.newCommandHandlerWithReplyBuilder();
        builder.forAnyState()
            .onCommand(
                GlobalCommand.GetArtifacts.class,
                (state, cmd) -> this.Effect().reply(cmd.replyTo(), state.artifacts())
            )
            .onCommand(
                GlobalCommand.RegisterArtifact.class,
                this::handleRegisterGroup
            );
        return builder.build();
    }

    private ReplyEffect<GlobalEvent, GlobalState> handleRegisterGroup(
        GlobalState state, GlobalCommand.RegisterArtifact cmd
    ) {
        if (!state.artifacts().contains(cmd.artifact())) {
            return this.Effect()
                .persist(new GlobalEvent.ArtifactRegistered(cmd.artifact()))
                .thenReply(cmd.replyTo(), (s) -> Done.done());
        }
        return this.Effect().reply(cmd.replyTo(), Done.done());
    }

}
