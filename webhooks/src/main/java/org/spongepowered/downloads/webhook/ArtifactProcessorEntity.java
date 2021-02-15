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
package org.spongepowered.downloads.webhook;

import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventHandlerBuilder;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;
import com.lightbend.lagom.javadsl.persistence.AkkaTaggerAdapter;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import io.vavr.collection.HashMap;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.spongepowered.downloads.webhook.sonatype.Component;
import org.spongepowered.downloads.webhook.sonatype.SonatypeClient;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public class ArtifactProcessorEntity
    extends EventSourcedBehaviorWithEnforcedReplies<ArtifactSagaCommand, ScrapedArtifactEvent, ProcessingState> {

    public static final Logger LOGGER = LogManager.getLogger("ArtifactProcessorEntity");
    public static final Marker DATA_RETRIEVAL = MarkerManager.getMarker("READ");
    public static final Marker COMMAND_PROCESS = MarkerManager.getMarker("COMMAND");
    public static final Marker EVENT_TRIGGER = MarkerManager.getMarker("EVENT_TRIGGERED");
    public static final Marker ARTIFACT_RESOLUTION = MarkerManager.getMarker("COLLISION_RESOLUTION");
    public static EntityTypeKey<ArtifactSagaCommand> ENTITY_TYPE_KEY = EntityTypeKey.create(
        ArtifactSagaCommand.class, "ArtifactCollection");
    private final String groupId;
    private final Function<ScrapedArtifactEvent, Set<String>> tagger;


    public static ArtifactProcessorEntity create(final EntityContext<ArtifactSagaCommand> context) {
        return new ArtifactProcessorEntity(context);
    }

    @Override
    public CommandHandlerWithReply<ArtifactSagaCommand, ScrapedArtifactEvent, ProcessingState> commandHandler() {
        final var builder = this.newCommandHandlerWithReplyBuilder();
        builder.forAnyState()
            .onCommand(ArtifactSagaCommand.StartProcessing.class, this::handleStartProcessing)
            .onCommand( ArtifactSagaCommand.AssociateMetadataWithCollection.class, this::respondToMetadataAssociation);
        return builder.build();
    }

    @Override
    public ProcessingState emptyState() {
        return new ProcessingState.EmptyState();
    }

    @Override
    public EventHandler<ProcessingState, ScrapedArtifactEvent> eventHandler() {
        final var builder = this.newEventHandlerBuilder();
        builder.forAnyState()
            .onEvent(ScrapedArtifactEvent.InitializeArtifactForProcessing.class, this::initializeFromEvent)
            .onEvent(ScrapedArtifactEvent.AssociatedMavenMetadata.class, this::associateSonatypeInformation)
            .onEvent(ScrapedArtifactEvent.AssociateCommitSha.class, this::handleCommitShaAssociation)
            .onEvent(ScrapedArtifactEvent.ArtifactRequested.class, this::handleArtifactRequested);
        return builder.build();
    }

    private ArtifactProcessorEntity(final EntityContext<ArtifactSagaCommand> context) {
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


    private ReplyEffect<ScrapedArtifactEvent, ProcessingState> handleStartProcessing(
        final ProcessingState state,
        final ArtifactSagaCommand.StartProcessing cmd
    ) {
        LOGGER.log(Level.INFO, COMMAND_PROCESS, "State[{}] processing command: {}", state, cmd);
        final var mavenCoordinates = cmd.artifact;
        final String componentId = cmd.webhook.component().componentId();

        final var events = new ArrayList<ScrapedArtifactEvent>();
        if (state.getCoordinates().map(coords -> !coords.equals(mavenCoordinates)).orElse(true)) {
            LOGGER.log(Level.INFO, EVENT_TRIGGER, "State[{}] will add a new event for initializing the process of an artifact: {}", state, mavenCoordinates);

            events.add(
                new ScrapedArtifactEvent.InitializeArtifactForProcessing(
                    mavenCoordinates, cmd.webhook.repositoryName(), componentId)
            );
        }
        return this.Effect().persist(events).thenReply(cmd.replyTo, (s) -> NotUsed.notUsed());
    }

    private ProcessingState initializeFromEvent(
        final ScrapedArtifactEvent.InitializeArtifactForProcessing event
    ) {
        return new ProcessingState.MetadataState(event.coordinates, event.repository(), HashMap.empty());
    }

    private ReplyEffect<ScrapedArtifactEvent, ProcessingState> respondToMetadataAssociation(
        final ProcessingState state,
        final ArtifactSagaCommand.AssociateMetadataWithCollection cmd
    ) {
        if (state.hasMetadata()) {
            return this.Effect().reply(cmd.replyTo, NotUsed.notUsed());
        }
        return this.Effect().persist(new ScrapedArtifactEvent.AssociatedMavenMetadata(
            cmd.collection,
            cmd.collection.coordinates.toString(),
            cmd.tagVersion,
            cmd.component.assets().toMap(Component.Asset::path, asset -> new Tuple2<>(asset.id(), asset.downloadUrl()))
        )).thenReply(cmd.replyTo, (s) -> NotUsed.notUsed());
    }

    private ProcessingState associateSonatypeInformation(final ProcessingState state, final ScrapedArtifactEvent.AssociatedMavenMetadata event) {
        return new ProcessingState.MetadataState(
            event.collection().coordinates,
            state.getRepository().get(),
            event.artifactPathToSonatypeId()
        );
    }


    private ProcessingState handleArtifactRequested(final ProcessingState state, final ScrapedArtifactEvent.ArtifactRequested event) {
        if (state.getCoordinates().map(coords -> !coords.equals(event.mavenCoordinates())).orElse(true)) {
            return new ProcessingState.MetadataState(
                event.coordinates, SonatypeClient.getConfig().getPublicRepo(), HashMap.empty());
        }
        return state;
    }

    private ProcessingState handleCommitShaAssociation(final ProcessingState state, final ScrapedArtifactEvent.AssociateCommitSha event) {
        if (state.hasCommit()) {
            return state;
        }
        return new ProcessingState.CommittedState(
            event.mavenCoordinates(),
            state.getRepository().get(),
            state.getArtifacts().get(),
            event.commit()
        );
    }

}
