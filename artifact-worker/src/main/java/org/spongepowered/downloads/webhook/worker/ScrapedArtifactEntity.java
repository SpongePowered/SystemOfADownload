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
package org.spongepowered.downloads.webhook.worker;

import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.Eventsourced;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.CommandHandlerWithReplyBuilder;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;
import com.lightbend.lagom.javadsl.persistence.AkkaTaggerAdapter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.webhook.ScrapedArtifactEvent;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;

public class ScrapedArtifactEntity extends EventSourcedBehaviorWithEnforcedReplies<ScrapedArtifactCommand, ScrapedArtifactEvent, ScrapedProcessingState> {

    public static final Logger LOGGER = LogManager.getLogger("ScrapedArtifactEntity");
    public static final Marker DATA_RETRIEVAL = MarkerManager.getMarker("READ");
    public static final Marker ARTIFACT_RESOLUTION = MarkerManager.getMarker("COLLISION_RESOLUTION");
    public static EntityTypeKey<ScrapedArtifactCommand> ENTITY_TYPE_KEY = EntityTypeKey.create(ScrapedArtifactCommand.class, "ArtifactCollection");
    private final String groupId;
    private final Function<ScrapedArtifactEvent, Set<String>> tagger;

    public static ScrapedArtifactEntity create(final EntityContext<ScrapedArtifactCommand> context) {
        return new ScrapedArtifactEntity(context);
    }

    private ScrapedArtifactEntity(final EntityContext<ScrapedArtifactCommand> context) {
        super(
            // PersistenceId needs a typeHint (or namespace) and entityId,
            // we take then from the EntityContext
            PersistenceId.of(
                context.getEntityTypeKey().name(), // <- type hint
                context.getEntityId() // <- business id
            ));
        // we keep a copy of cartI
        this.groupId = context.getEntityId();
        this.tagger = AkkaTaggerAdapter.fromLagom(context, ScrapedArtifactEvent.INSTANCE);
    }

    @Override
    public ScrapedProcessingState emptyState() {
        return new ScrapedProcessingState.EmptyState();
    }

    @Override
    public CommandHandlerWithReply<ScrapedArtifactCommand, ScrapedArtifactEvent, ScrapedProcessingState> commandHandler() {
        final var builder = this.newCommandHandlerWithReplyBuilder();
        builder.forAnyState()
            .onCommand(ScrapedArtifactCommand.AssociateMetadataWithCollection.class, this::respondToAssociateMetadataForCollection)
            .onCommand(ScrapedArtifactCommand.RequestArtifactForProcessing.class, this::respondRequestArtifactForProcessing)
            .onCommand(ScrapedArtifactCommand.AssociateCommitShaWithArtifact.class, this::respondToAssociatingCommitShaWithArtifact);
        return builder.build();
    }

    @Override
    public EventHandler<ScrapedProcessingState, ScrapedArtifactEvent> eventHandler() {
        final var builder = this.newEventHandlerBuilder();
        return builder.build();
    }

    private ReplyEffect<ScrapedArtifactEvent, ScrapedProcessingState> respondToAssociateMetadataForCollection(
        final ScrapedProcessingState state,
        final ScrapedArtifactCommand.AssociateMetadataWithCollection cmd
    ) {
        LOGGER.log(Level.INFO, "Got state {} with cmd {}", state, cmd);
        return this.Effect().reply(cmd.replyTo, NotUsed.notUsed());
    }


    private ReplyEffect<ScrapedArtifactEvent, ScrapedProcessingState> respondToAssociatingCommitShaWithArtifact(
        final ScrapedProcessingState state,
        final ScrapedArtifactCommand.AssociateCommitShaWithArtifact cmd
    ) {
        if (!state.hasCommit()) {
            return this.Effect().persist(new ScrapedArtifactEvent.AssociateCommitSha(
                cmd.collection,
                cmd.sha
            ))
                .thenReply(cmd.replyTo, (s) -> NotUsed.notUsed());
        }
        return this.Effect()
            .reply(cmd.replyTo, NotUsed.notUsed());
    }

    private ReplyEffect<ScrapedArtifactEvent, ScrapedProcessingState> respondRequestArtifactForProcessing(
        final ScrapedProcessingState state,
        final ScrapedArtifactCommand.RequestArtifactForProcessing cmd
    ) {
        final String mavenCoordinates = new StringJoiner(":").add(cmd.groupId).add(cmd.artifactId).add(cmd.requested).toString();

        if (state.getCoordinates().map(coords -> !coords.equals(mavenCoordinates)).orElse(true)) {
            final var parse = MavenCoordinates.parse(mavenCoordinates);
            return this.Effect()
                .persist(new ScrapedArtifactEvent.ArtifactRequested(parse))
                .thenReply(cmd.replyTo, (s) -> NotUsed.notUsed());
        }
        return this.Effect().reply(cmd.replyTo, NotUsed.notUsed());
    }
}
