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
import akka.persistence.typed.javadsl.RetentionCriteria;
import com.lightbend.lagom.javadsl.persistence.AkkaTaggerAdapter;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.versions.api.models.TagRegistration;
import org.spongepowered.downloads.versions.api.models.TagVersion;
import org.spongepowered.downloads.versions.api.models.VersionRegistration;

import java.util.ArrayList;
import java.util.Locale;
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
            .onEvent(ACEvent.ArtifactCoordinatesUpdated.class,
                (state3, event3) -> state3.withCoordinates(event3.coordinates)
            )
            .onEvent(ACEvent.ArtifactVersionRegistered.class,
                (state2, event2) -> state2.withVersion(event2.version.version)
            )
            ;
        builder.forState(ACState::isRegistered)
            .onEvent(ACEvent.ArtifactTagRegistered.class, (state1, event1) -> state1.withTag(event1.entry()))
            .onEvent(ACEvent.VersionTagged.class, (state, event) -> state)
            .onEvent(ACEvent.PromotionSettingModified.class,
                (state, event) -> state.withPromotionDetails(event.regex(), event.enableManualPromotion())
            )
        ;
        return builder.build();
    }

    @Override
    public Set<String> tagsFor(final ACEvent acEvent) {
        return this.tagger.apply(acEvent);
    }

    @Override
    public RetentionCriteria retentionCriteria() {
        return RetentionCriteria.snapshotEvery(10, 2);
    }

    @Override
    public CommandHandlerWithReply<ACCommand, ACEvent, ACState> commandHandler() {
        final var builder = this.newCommandHandlerWithReplyBuilder();
        builder.forState(ACState::isRegistered)
            .onCommand(ACCommand.RegisterArtifact.class, (cmd) -> this.Effect().reply(cmd.replyTo, NotUsed.notUsed()))
            .onCommand(ACCommand.RegisterVersion.class, this::handleRegisterVersion)
            .onCommand(ACCommand.RegisterArtifactTag.class, this::handlRegisterTag)
            .onCommand(ACCommand.UpdateArtifactTag.class, this::handleUpdateTag)
            .onCommand(ACCommand.RegisterPromotion.class, this::handlePromotionSetting)
        ;
        builder.forAnyState()
            .onCommand(ACCommand.RegisterVersion.class, (cmd) -> this.Effect().reply(cmd.replyTo(), new VersionRegistration.Response.GroupMissing(cmd.coordinates().groupId)))
            .onCommand(ACCommand.RegisterArtifact.class, this::handleRegisterArtifact);
        return builder.build();
    }

    private ReplyEffect<ACEvent, ACState> handleRegisterVersion(
        final ACState state, final ACCommand.RegisterVersion cmd
    ) {
        if (state.collection().containsKey(cmd.coordinates().version)) {
            return this.Effect().reply(
                cmd.replyTo(),
                new VersionRegistration.Response.ArtifactAlreadyRegistered(cmd.coordinates().asArtifactCoordinates())
            );
        }
        return this.Effect()
            .persist(new ACEvent.ArtifactVersionRegistered(cmd.coordinates()))
            .thenReply(cmd.replyTo(), (s) -> new VersionRegistration.Response.RegisteredArtifact(cmd.coordinates()));
    }

    private ReplyEffect<ACEvent, ACState> handleRegisterArtifact(
        final ACState state,
        final ACCommand.RegisterArtifact cmd
    ) {
        return this.Effect()
            .persist(new ACEvent.ArtifactCoordinatesUpdated(cmd.coordinates))
            .thenReply(cmd.replyTo, (s) -> NotUsed.notUsed());
    }

    private ReplyEffect<ACEvent, ACState> handlRegisterTag(
        final ACState state,
        final ACCommand.RegisterArtifactTag cmd
    ) {
        if (state.tags().containsKey(cmd.entry().name().toLowerCase(Locale.ROOT))) {
            return this.Effect().reply(cmd.replyTo(), new TagRegistration.Response.TagAlreadyRegistered(cmd.entry().name()));
        }
        final var events = new ArrayList<ACEvent>();
        events.add(new ACEvent.ArtifactTagRegistered(state.coordinates(), cmd.entry()));
        state.collection()
            .forEach((version, tag) -> events.add(new ACEvent.VersionTagged(tag.coordinates(), cmd.entry().generateValue(state.coordinates().version(version)))));
        return this.Effect()
            .persist(events)
            .thenReply(cmd.replyTo(), (s) -> new TagRegistration.Response.TagSuccessfullyRegistered());
    }

    private ReplyEffect<ACEvent, ACState> handlePromotionSetting(
        final ACState state,
        final ACCommand.RegisterPromotion cmd
    ) {
        return this.Effect()
            .persist(new ACEvent.PromotionSettingModified(state.coordinates(), cmd.regex(), cmd.enableManualMarking()))
            .thenReply(cmd.replyTo(), (s) -> new TagVersion.Response.TagSuccessfullyRegistered());
    }

    private ReplyEffect<ACEvent, ACState> handleUpdateTag(
        final ACState state,
        final ACCommand.UpdateArtifactTag cmd
    ) {
        return this.Effect()
            .persist(new ACEvent.ArtifactTagRegistered(state.coordinates(), cmd.entry()))
            .thenReply(cmd.replyTo(), (s) -> new TagRegistration.Response.TagSuccessfullyRegistered());
    }

}
