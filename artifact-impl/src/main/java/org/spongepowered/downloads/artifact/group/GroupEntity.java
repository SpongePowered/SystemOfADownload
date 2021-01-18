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
package org.spongepowered.downloads.artifact.group;

import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.CommandHandlerWithReplyBuilder;
import akka.persistence.typed.javadsl.EffectFactories;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventHandlerBuilder;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;
import com.lightbend.lagom.javadsl.persistence.AkkaTaggerAdapter;
import io.vavr.collection.HashSet;
import io.vavr.control.Try;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;

import java.net.URL;
import java.util.Set;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public class GroupEntity
    extends EventSourcedBehaviorWithEnforcedReplies<GroupCommand, GroupEvent, GroupState> {

    public static final Logger LOGGER = LogManager.getLogger("GroupEntity");
    public static final Marker STATE_RETRIEVAL = MarkerManager.getMarker("READ");
    public static final Marker STATE_MUTATION = MarkerManager.getMarker("CHANGES");
    public static EntityTypeKey<GroupCommand> ENTITY_TYPE_KEY = EntityTypeKey.create(GroupCommand.class, "GroupEntity");
    private final String groupId;
    private final Function<GroupEvent, Set<String>> tagger;

    private GroupEntity(EntityContext<GroupCommand> context) {
        super(
            // PersistenceId needs a typeHint (or namespace) and entityId,
            // we take then from the EntityContext
            PersistenceId.of(
                context.getEntityTypeKey().name(), // <- type hint
                context.getEntityId() // <- business id
            ));
        // we keep a copy of cartI
        this.groupId = context.getEntityId();
        this.tagger = AkkaTaggerAdapter.fromLagom(context, GroupEvent.TAG);

    }

    public static GroupEntity create(EntityContext<GroupCommand> context) {
        return new GroupEntity(context);
    }

    @Override
    public GroupState emptyState() {
        return GroupState.empty();
    }

    @Override
    public EventHandler<GroupState, GroupEvent> eventHandler() {
        final EventHandlerBuilder<GroupState, GroupEvent> builder = this.newEventHandlerBuilder();

        builder.forState(GroupState::isEmpty)
            .onEvent(
                GroupEvent.GroupRegistered.class,
                this::handleRegistration
            );
        builder.forAnyState()
            .onEvent(GroupEvent.ArtifactRegistered.class, this::handleArtifactRegistration);

        return builder.build();
    }

    private GroupState handleRegistration(
        final GroupState state, final GroupEvent.GroupRegistered event
    ) {
        LOGGER.info(
            STATE_MUTATION,
            "Mutating state {} with {}",
            state,
            event
        );
        return new GroupState(event.groupId, event.name, event.website, HashSet.empty());
    }

    private GroupState handleArtifactRegistration(
        final GroupState state, final GroupEvent.ArtifactRegistered event
    ) {
        LOGGER.info(
            STATE_MUTATION,
            "Mutating state {} with {}",
            state,
            event
        );
        final var add = state.artifacts.add(event.artifact);
        LOGGER.info(
            STATE_MUTATION,
            "Adding artifact {} to existing set {}",
            event.artifact,
            state.artifacts
        );
        return new GroupState(state.groupCoordinates, state.name, state.website, add);
    }

    @Override
    public CommandHandlerWithReply<GroupCommand, GroupEvent, GroupState> commandHandler() {
        final CommandHandlerWithReplyBuilder<GroupCommand, GroupEvent, GroupState> builder = this.newCommandHandlerWithReplyBuilder();

        builder.forAnyState()
            .onCommand(GroupCommand.RegisterGroup.class, this::respondToRegisterGroup)
            .onCommand(GroupCommand.RegisterArtifact.class, this::respondToRegisterArtifact)
            .onCommand(GroupCommand.GetGroup.class, this::respondToGetGroup)
            .onCommand(GroupCommand.GetArtifacts.class, this::respondToGetVersions);
        return builder.build();
    }

    private ReplyEffect<GroupEvent, GroupState> respondToRegisterGroup(
        final GroupState state,
        final GroupCommand.RegisterGroup cmd
    ) {
        if (!state.groupCoordinates.equals(cmd.mavenCoordinates)) {
            return this.Effect()
                .persist(new GroupEvent.GroupRegistered(cmd.mavenCoordinates, cmd.name, cmd.website))
                .thenReply(
                    cmd.replyTo,
                    newState -> new GroupRegistration.Response.GroupRegistered(
                        new Group(
                            newState.groupCoordinates,
                            newState.name,
                            newState.website
                        ))
                );
        }
        return this.Effect().reply(
            cmd.replyTo, new GroupRegistration.Response.GroupAlreadyRegistered(cmd.mavenCoordinates));
    }

    private ReplyEffect<GroupEvent, GroupState> respondToRegisterArtifact(
        final GroupState state,
        final GroupCommand.RegisterArtifact cmd
    ) {
        if (state.groupCoordinates.isEmpty()) {
            return this.Effect().reply(cmd.replyTo, new ArtifactRegistration.Response.GroupMissing(state.name));
        }
        if (state.artifacts.contains(cmd.artifact)) {
            this.Effect().reply(cmd.replyTo, new ArtifactRegistration.Response.ArtifactAlreadyRegistered(
                cmd.artifact,
                state.groupCoordinates
            ));
        }

        final var group = state.asGroup();
        final var collection = new ArtifactCollection(group, cmd.artifact, "");
        final EffectFactories<GroupEvent, GroupState> effect = this.Effect();
        return effect.persist(new GroupEvent.ArtifactRegistered(state.groupCoordinates, cmd.artifact))
            .thenReply(cmd.replyTo, (s) -> new ArtifactRegistration.Response.RegisteredArtifact(collection));
    }

    private ReplyEffect<GroupEvent, GroupState> respondToGetGroup(
        final GroupState state, final GroupCommand.GetGroup cmd
    ) {
        LOGGER.info(STATE_RETRIEVAL, "CurrentState: {} responding to cmd {}", state.name, cmd);
        if (state.name.equalsIgnoreCase(cmd.groupId)) {

            LOGGER.info(STATE_RETRIEVAL, "Group Matched, getting response from Stfate: {}", state);
            final String website = state.website;
            return this.Effect().reply(cmd.replyTo, Try.of(() -> new URL(website))
                .<GroupResponse>mapTry(url -> {
                    LOGGER.info(STATE_RETRIEVAL, "URL matched");
                    final Group group = new Group(state.groupCoordinates, state.name, website);
                    LOGGER.info(
                        STATE_RETRIEVAL, "Retrieved Group: (coords: {}, name: {}, website: {})", group.groupCoordinates,
                        group.name, group.website
                    );
                    return new GroupResponse.Available(group);
                })
                .getOrElseGet(throwable -> new GroupResponse.Missing(cmd.groupId)));
        }
        LOGGER.info(STATE_RETRIEVAL, "GroupId mismatch, requested {} but had {}", state.name, cmd.groupId);
        return this.Effect().reply(cmd.replyTo, new GroupResponse.Missing(cmd.groupId));
    }

    private ReplyEffect<GroupEvent, GroupState> respondToGetVersions(
        final GroupState state,
        final GroupCommand.GetArtifacts cmd
    ) {
        LOGGER.info(STATE_RETRIEVAL, "CurrentState: {} responding to cmd {}", state.name, cmd);
        if (!state.name.equalsIgnoreCase(cmd.groupId)) {
            LOGGER.info(STATE_RETRIEVAL, "GroupId mismatch, requested {} but had {}", state.name, cmd.groupId);
            return this.Effect().reply(cmd.replyTo, new GetArtifactsResponse.GroupMissing(cmd.groupId));
        }
        LOGGER.info(STATE_RETRIEVAL, "Group Matched, getting response from Stfate: {}", state);
        final String website = state.website;
        final Group group = new Group(state.groupCoordinates, state.name, website);
        LOGGER.info(
            STATE_RETRIEVAL, "Retrieved Group: (coords: {}, name: {}, website: {})", group.groupCoordinates,
            group.name, group.website
        );
        LOGGER.info(
            STATE_RETRIEVAL,
            "Artifacts available for group: {} [{}]",
            state.name,
            state.artifacts.toJavaList()
        );
        return this.Effect().reply(cmd.replyTo, new GetArtifactsResponse.ArtifactsAvailable(state.artifacts.toList()));
    }
}
