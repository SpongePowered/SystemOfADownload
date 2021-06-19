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
package org.spongepowered.downloads.versions.sonatype.resync;

import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.japi.function.Function;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.sonatype.client.SonatypeClient;

public final class ArtifactSynchronizerAggregate
    extends EventSourcedBehaviorWithEnforcedReplies<Resync, SynchronizeEvent, SyncState> {
    public static EntityTypeKey<Resync> ENTITY_TYPE_KEY = EntityTypeKey.create(Resync.class, "ArtifactSynchronizer");
    static final Function<SyncState, List<MavenCoordinates>> stateToCoordinates = (s) -> s.versions.versioning().versions.map(
        version -> MavenCoordinates.parse(s.groupId + ":" + s.artifactId + ":" + version));
    private final SonatypeClient client;

    public ArtifactSynchronizerAggregate(EntityContext<Resync> context) {
        super(
            // PersistenceId needs a typeHint (or namespace) and entityId,
            // we take then from the EntityContext
            PersistenceId.of(
                context.getEntityTypeKey().name(), // <- type hint
                context.getEntityId() // <- business id
            ));
        final var mapper = new ObjectMapper();
        this.client = SonatypeClient.configureClient(mapper).get();
    }

    public static ArtifactSynchronizerAggregate create(EntityContext<Resync> context) {
        return new ArtifactSynchronizerAggregate(context);
    }

    @Override
    public SyncState emptyState() {
        return SyncState.EMPTY;
    }

    @Override
    public EventHandler<SyncState, SynchronizeEvent> eventHandler() {
        final var builder = newEventHandlerBuilder()
            .forAnyState()
            .onEvent(
                SynchronizeEvent.SynchronizedArtifacts.class,
                (event) -> new SyncState(event.updatedTime, event.metadata)
            );
        return builder.build();
    }

    @Override
    public CommandHandlerWithReply<Resync, SynchronizeEvent, SyncState> commandHandler() {
        final var builder = this.newCommandHandlerWithReplyBuilder()
            .forAnyState()
            .onCommand(Resync.class, this::handleResync);
        return builder.build();
    }

    private ReplyEffect<SynchronizeEvent, SyncState> handleResync(SyncState state, Resync cmd) {
        final var groupId = !state.groupId.equals(cmd.coordinates.groupId) ? cmd.coordinates.groupId : state.groupId;
        final var artifactId = !state.artifactId.equals(cmd.coordinates.artifactId) ? cmd.coordinates.artifactId : state.artifactId;
        return this.client.getArtifactMetadata(groupId.replace(".", "/"), artifactId)
            .mapTry(metadata -> {
                if (metadata.versioning().lastUpdated.equals(state.lastUpdated)) {
                    return this.Effect()
                        .reply(cmd.replyTo, List.empty());
                }
                return this.Effect()
                    .persist(new SynchronizeEvent.SynchronizedArtifacts(metadata, metadata.versioning().lastUpdated))
                    .thenReply(cmd.replyTo, stateToCoordinates);
            })
            .getOrElseGet((ignored) -> this.Effect().reply(cmd.replyTo, List.empty()));
    }

}
