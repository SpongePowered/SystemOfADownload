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
package org.spongepowered.downloads.versions.collection;

import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;
import com.lightbend.lagom.javadsl.api.transport.NotFound;
import com.lightbend.lagom.javadsl.persistence.AkkaTaggerAdapter;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.versions.api.models.GetVersionResponse;
import org.spongepowered.downloads.versions.api.models.GetVersionsResponse;
import org.spongepowered.downloads.versions.api.models.VersionRegistration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Function;

public final class VersionedArtifactAggregate
    extends EventSourcedBehaviorWithEnforcedReplies<ACCommand, ACEvent, ACState> {

    public static EntityTypeKey<ACCommand> ENTITY_TYPE_KEY = EntityTypeKey.create(ACCommand.class, "VersionedArtifact");
    private final Function<ACEvent, Set<String>> tagger;

    public static VersionedArtifactAggregate create(final EntityContext<ACCommand> context) {
        return new VersionedArtifactAggregate(context);
    }

    private VersionedArtifactAggregate(final EntityContext<ACCommand> context) {
        super(
            // PersistenceId needs a typeHint (or namespace) and entityId,
            // we take then from the EntityContext
            PersistenceId.of(
                context.getEntityTypeKey().name(), // <- type hint
                context.getEntityId() // <- business id
            ));
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
            .onEvent(ACEvent.ArtifactCoordinatesUpdated.class, this::updateCoordinates)
            .onEvent(ACEvent.ArtifactVersionRegistered.class, this::updateVersionRegistered);
        builder.forState(ACState::isRegistered)
            .onEvent(ACEvent.CollectionRegistered.class, this::updateCollections)
        ;
        return builder.build();
    }

    @Override
    public Set<String> tagsFor(final ACEvent acEvent) {
        return this.tagger.apply(acEvent);
    }

    @Override
    public CommandHandlerWithReply<ACCommand, ACEvent, ACState> commandHandler() {
        final var builder = this.newCommandHandlerWithReplyBuilder();
        builder.forState(ACState::isRegistered)
            .onCommand(ACCommand.RegisterCollection.class, this::handleRegisterCommand)
            .onCommand(ACCommand.RegisterArtifact.class, (cmd) -> this.Effect().reply(cmd.replyTo, NotUsed.notUsed()))
            .onCommand(ACCommand.GetSpecificVersion.class, (state, cmd) -> {
                final var versionedCollection = state.collection().get(cmd.version());
                if (versionedCollection.isEmpty()) {
                    return this.Effect().reply(cmd.replyTo(), new GetVersionResponse.VersionMissing(cmd.version()));
                }
                return this.Effect().reply(cmd.replyTo(), new GetVersionResponse.VersionInfo(versionedCollection.get()));
            })
        ;
        builder.forAnyState()
            .onCommand(ACCommand.RegisterVersion.class, this::handleRegisterVersion)
            .onCommand(ACCommand.RegisterArtifact.class, this::handleRegisterArtifact)
            .onCommand(ACCommand.GetVersions.class, this::respondToGetVersions);
        return builder.build();
    }

    private ReplyEffect<ACEvent, ACState> handleRegisterVersion(
        final ACState state, final ACCommand.RegisterVersion cmd
    ) {
        if (state.collection().containsKey(cmd.coordinates.version)) {
            return this.Effect().reply(
                cmd.replyTo,
                new VersionRegistration.Response.ArtifactAlreadyRegistered(cmd.coordinates.asArtifactCoordinates())
            );
        }
        return this.Effect()
            .persist(new ACEvent.ArtifactVersionRegistered(cmd.coordinates.version,
                new ArtifactCollection(HashMap.empty(), cmd.coordinates)
            ))
            .thenReply(cmd.replyTo, (s) -> new VersionRegistration.Response.RegisteredArtifact(cmd.coordinates));
    }

    private ReplyEffect<ACEvent, ACState> handleRegisterCommand(
        final ACState state,
        final ACCommand.RegisterCollection cmd
    ) {
        if (!state.coordinates().groupId.equals(cmd.collection.coordinates.groupId)) {
            return this.Effect().reply(
                cmd.replyTo, new VersionRegistration.Response.GroupMissing(cmd.collection.coordinates.groupId));
        }
        final var events = new ArrayList<ACEvent>();
        if (!state.collection().get(cmd.collection.coordinates.version)
            .exists(existing -> existing.getArtifactComponents().equals(cmd.collection.getArtifactComponents()))) {
            events.add(new ACEvent.CollectionRegistered(cmd.collection));
        }
        return this.Effect().persist(events).thenReply(cmd.replyTo, (s) -> {
            if (events.isEmpty()) {
                return new VersionRegistration.Response.ArtifactAlreadyRegistered(s.coordinates());
            }
            return new VersionRegistration.Response.RegisteredArtifact(
                cmd.collection.artifactComponents, cmd.collection.coordinates);
        });
    }

    private ReplyEffect<ACEvent, ACState> handleRegisterArtifact(
        final ACState state,
        final ACCommand.RegisterArtifact cmd
    ) {
        return this.Effect()
            .persist(new ACEvent.ArtifactCoordinatesUpdated(cmd.coordinates))
            .thenReply(cmd.replyTo, (s) -> NotUsed.notUsed());
    }


    private ACState updateCoordinates(
        final ACState state, final ACEvent.ArtifactCoordinatesUpdated event
    ) {
        return new ACState(event.coordinates, state.collection(), false);
    }

    private ACState updateVersionRegistered(
        final ACState state, final ACEvent.ArtifactVersionRegistered event
    ) {
        final Map<String, ArtifactCollection> newMap = state
            .collection()
            .computeIfAbsent(event.version, (version) -> event.collection)
            ._2;
        final var sorted = newMap
            .toSortedMap(Comparator.reverseOrder(), (key) -> key._1, (tuple) -> tuple._2);
        return new ACState(
            event.collection.coordinates.asArtifactCoordinates(),
            sorted,
            false
        );
    }

    private ACState updateCollections(
        final ACState state, final ACEvent.CollectionRegistered event
    ) {
        final var version = event.collection.coordinates.version;
        final var updatedComponents = state.collection().get(version)
            .map(ArtifactCollection::getArtifactComponents)
            .map(existing -> existing.merge(
                event.collection.getArtifactComponents(),
                (existingArtifact, newArtifact) -> {
                    if (existingArtifact.equals(newArtifact)) {
                        return existingArtifact;
                    }
                    return newArtifact;
                }
                )
            )
            .getOrElse(event.collection::getArtifactComponents);
        final var updatedCollection = new ArtifactCollection(updatedComponents, event.collection.coordinates);
        final var updatedVersionedCollections = state.collection().put(version, updatedCollection);
        return new ACState(state.coordinates(), updatedVersionedCollections, false);
    }

    private ReplyEffect<ACEvent, ACState> respondToGetVersions(
        final ACState state,
        final ACCommand.GetVersions cmd
    ) {
        if (!state.coordinates().artifactId.equalsIgnoreCase(cmd.artifactId)) {
            return this.Effect().reply(cmd.replyTo, new GetVersionsResponse.ArtifactUnknown(cmd.artifactId));
        }
        return this.Effect().reply(cmd.replyTo, new GetVersionsResponse.VersionsAvailable(state.collection()));
    }

}
