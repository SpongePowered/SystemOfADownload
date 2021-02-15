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
package org.spongepowered.downloads.artifact.tags;

import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.CommandHandlerWithReplyBuilder;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventHandlerBuilder;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import io.vavr.collection.TreeMap;
import org.spongepowered.downloads.artifact.api.query.GetTaggedArtifacts;
import org.spongepowered.downloads.artifact.collection.ACCommand;
import org.spongepowered.downloads.artifact.collection.ArtifactCollectionEntity;

import java.util.Optional;

public class TaggedVersionEntity
    extends EventSourcedBehaviorWithEnforcedReplies<TaggedCommand, TaggedEvent, TaggedState> {

    /**
     * Persistence based type key for this aggregate.
     */
    public static EntityTypeKey<TaggedCommand> ENTITY_TYPE_KEY = EntityTypeKey.create(TaggedCommand.class, "TaggedVersion");

    /**
     * Creates a new {@code TaggedVersionEntity} from the given context.
     *
     * @param context The entity context, passed by Akka
     * @return The newly constructed entity with behaviors
     */
    public static TaggedVersionEntity create(final EntityContext<TaggedCommand> context) {
        return new TaggedVersionEntity(context);
    }

    private TaggedVersionEntity(final EntityContext<TaggedCommand> context) {
        super(
            // PersistenceId needs a typeHint (or namespace) and entityId,
            // we take then from the EntityContext
            PersistenceId.of(
                context.getEntityTypeKey().name(), // <- type hint
                context.getEntityId() // <- business id
            ));
    }

    private ReplyEffect<TaggedEvent, TaggedState> handleRequestVersions(
        final TaggedState state,
        final TaggedCommand.RequestTaggedVersions cmd
    ) {
        return this.Effect().reply(cmd.replyTo, new GetTaggedArtifacts.Response.VersionsAvailable(TreeMap.empty()));
    }

    @Override
    public TaggedState emptyState() {
        return new TaggedState.EmptyState();
    }

    @Override
    public EventHandler<TaggedState, TaggedEvent> eventHandler() {
        final var builder = this.newEventHandlerBuilder();

        return builder.build();
    }

    @Override
    public CommandHandlerWithReply<TaggedCommand, TaggedEvent, TaggedState> commandHandler() {
        final var builder = this.newCommandHandlerWithReplyBuilder();
        builder.forAnyState()
            .onCommand(TaggedCommand.RequestTaggedVersions.class, this::handleRequestVersions);
        return builder.build();
    }
}
