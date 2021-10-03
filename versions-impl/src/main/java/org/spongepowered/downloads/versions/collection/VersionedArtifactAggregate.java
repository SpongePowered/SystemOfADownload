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

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;
import akka.persistence.typed.javadsl.RetentionCriteria;
import com.lightbend.lagom.javadsl.persistence.AkkaTaggerAdapter;
import io.vavr.collection.List;
import org.spongepowered.downloads.versions.api.models.TagRegistration;
import org.spongepowered.downloads.versions.api.models.TagVersion;
import org.spongepowered.downloads.versions.api.models.VersionRegistration;
import org.spongepowered.downloads.versions.commit.actor.VersionedAssetWorker;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

public final class VersionedArtifactAggregate
    extends EventSourcedBehaviorWithEnforcedReplies<ACCommand, ACEvent, State> {

    public static EntityTypeKey<ACCommand> ENTITY_TYPE_KEY = EntityTypeKey.create(ACCommand.class, "VersionedArtifact");
    private final Function<ACEvent, Set<String>> tagger;
    private final ActorContext<ACCommand> ctx;
    private final ActorRef<VersionedAssetWorker.Command> commitResolver;
    private final ActorRef<Done> doneActorRef;

    public static Behavior<ACCommand> create(final EntityContext<ACCommand> context) {
        return Behaviors.setup(ctx -> {
            final var commitWorker = VersionedAssetWorker.configure(ctx.getSystem().classicSystem());
            final var assetFetcherUID = UUID.randomUUID();
            final var commitWorkers = ctx.spawn(commitWorker, "asset-commit-fetcher-" + assetFetcherUID, DispatcherSelector.defaultDispatcher());

            final var dummyReceiver = VersionedArtifactAggregate.commitReceiver();
            final var doneActorRef = ctx.spawnAnonymous(dummyReceiver);
            return new VersionedArtifactAggregate(context, ctx, commitWorkers, doneActorRef);
        });
    }

    private static Behavior<Done> commitReceiver() {
        return Behaviors.receive(Done.class)
            .onAnyMessage(d -> Behaviors.same())
            .build();
    }

    private VersionedArtifactAggregate(
        final EntityContext<ACCommand> context,
        final ActorContext<ACCommand> ctx,
        final ActorRef<VersionedAssetWorker.Command> commitWorkers,
        final ActorRef<Done> doneActorRef
    ) {
        super(
            // PersistenceId needs a typeHint (or namespace) and entityId,
            // we take then from the EntityContext
            PersistenceId.of(
                context.getEntityTypeKey().name(), // <- type hint
                context.getEntityId() // <- business id
            ));
        this.tagger = AkkaTaggerAdapter.fromLagom(context, ACEvent.INSTANCE);
        this.ctx = ctx;
        this.commitResolver = commitWorkers;
        this.doneActorRef = doneActorRef;
    }

    @Override
    public State emptyState() {
        return State.empty();
    }

    @Override
    public EventHandler<State, ACEvent> eventHandler() {
        final var builder = this.newEventHandlerBuilder();
        builder.forStateType(State.Empty.class)
            .onEvent(ACEvent.ArtifactCoordinatesUpdated.class, State.Empty::register);
        builder.forStateType(State.ACState.class)
            .onEvent(ACEvent.ArtifactTagRegistered.class, (state1, event1) -> state1.withTag(event1.entry()))
            .onEvent(
                ACEvent.ArtifactVersionRegistered.class,
                (state2, event2) -> state2.withVersion(event2.version.version)
            )
            .onEvent(
                ACEvent.PromotionSettingModified.class,
                (state, event) -> state.withPromotionDetails(event.regex(), event.enableManualPromotion())
            )
            .onEvent(
                ACEvent.VersionedCollectionAdded.class,
                (state, event) -> state.withAddedArtifacts(event.collection().coordinates(), event.newArtifacts())
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
    public CommandHandlerWithReply<ACCommand, ACEvent, State> commandHandler() {
        final var builder = this.newCommandHandlerWithReplyBuilder();
        builder.forStateType(State.Empty.class)
            .onCommand(ACCommand.RegisterArtifact.class, this::handleRegisterArtifact)
            .onCommand(
                ACCommand.RegisterArtifactTag.class, (cmd) -> this.Effect().reply(cmd.replyTo(), new InvalidRequest()))
            .onCommand(
                ACCommand.RegisterVersion.class, (cmd) -> this.Effect().reply(cmd.replyTo(), new InvalidRequest()))
            .onCommand(
                ACCommand.RegisterArtifactTag.class, (cmd) -> this.Effect().reply(cmd.replyTo(), new InvalidRequest()))
            .onCommand(
                ACCommand.UpdateArtifactTag.class, (cmd) -> this.Effect().reply(cmd.replyTo(), new InvalidRequest()))
            .onCommand(
                ACCommand.RegisterPromotion.class, (cmd) -> this.Effect().reply(cmd.replyTo(), new InvalidRequest()))
            .onCommand(
                ACCommand.RegisterCollection.class, (cmd) -> this.Effect().reply(cmd.replyTo(), new InvalidRequest())
            )
        ;
        builder.forStateType(State.ACState.class)
            .onCommand(ACCommand.RegisterArtifact.class, (cmd) -> this.Effect().reply(cmd.replyTo, NotUsed.notUsed()))
            .onCommand(ACCommand.RegisterVersion.class, this::handleRegisterVersion)
            .onCommand(ACCommand.RegisterArtifactTag.class, this::handlRegisterTag)
            .onCommand(ACCommand.UpdateArtifactTag.class, this::handleUpdateTag)
            .onCommand(ACCommand.RegisterPromotion.class, this::handlePromotionSetting)
            .onCommand(ACCommand.RegisterCollection.class, this::handleRegisterCollection)
        ;
        return builder.build();
    }

    private ReplyEffect<ACEvent, State> handleRegisterVersion(
        final State.ACState state, final ACCommand.RegisterVersion cmd
    ) {
        if (state.collection().containsKey(cmd.coordinates().version)) {
            this.ctx.getLog().warn("[{}] Version re-registration attempted: {}", state.coordinates(), cmd.coordinates().version);
            return this.Effect().reply(
                cmd.replyTo(),
                new VersionRegistration.Response.ArtifactAlreadyRegistered(cmd.coordinates())
            );
        }
        return this.Effect()
            .persist(new ACEvent.ArtifactVersionRegistered(cmd.coordinates()))
            .thenReply(cmd.replyTo(), (s) -> new VersionRegistration.Response.RegisteredArtifact(cmd.coordinates()));
    }

    private ReplyEffect<ACEvent, State> handleRegisterCollection(
        final State.ACState state, final ACCommand.RegisterCollection cmd
    ) {
        if (!state.collection().containsKey(cmd.collection().coordinates().version)) {
            return this.Effect().reply(cmd.replyTo(), new InvalidRequest());
        }
        final var existing = state.versionedArtifacts().get(cmd.collection().coordinates().version)
            .getOrElse(List::empty);
        final var newArtifacts = cmd.collection().components().filter(Predicate.not(existing::contains));

        return this.Effect()
            .persist(new ACEvent.VersionedCollectionAdded(state.coordinates(), cmd.collection(), newArtifacts))
            .thenRun(s -> this.commitResolver.tell(new VersionedAssetWorker.FetchCommitFromAsset(cmd.collection(), this.doneActorRef)))
            .thenReply(
                cmd.replyTo(),
                (s) -> new VersionRegistration.Response.RegisteredArtifact(cmd.collection().coordinates())
            );
    }

    private ReplyEffect<ACEvent, State> handleRegisterArtifact(
        final State.Empty state,
        final ACCommand.RegisterArtifact cmd
    ) {
        return this.Effect()
            .persist(new ACEvent.ArtifactCoordinatesUpdated(cmd.coordinates))
            .thenReply(cmd.replyTo, (s) -> NotUsed.notUsed());
    }

    private ReplyEffect<ACEvent, State> handlRegisterTag(
        final State.ACState state,
        final ACCommand.RegisterArtifactTag cmd
    ) {
        if (state.tags().containsKey(cmd.entry().name().toLowerCase(Locale.ROOT))) {
            return this.Effect().reply(
                cmd.replyTo(), new TagRegistration.Response.TagAlreadyRegistered(cmd.entry().name()));
        }
        return this.Effect()
            .persist(new ACEvent.ArtifactTagRegistered(state.coordinates(), cmd.entry()))
            .thenReply(cmd.replyTo(), (s) -> new TagRegistration.Response.TagSuccessfullyRegistered());
    }

    private ReplyEffect<ACEvent, State> handlePromotionSetting(
        final State.ACState state,
        final ACCommand.RegisterPromotion cmd
    ) {
        return this.Effect()
            .persist(new ACEvent.PromotionSettingModified(state.coordinates(), cmd.regex(), cmd.enableManualMarking()))
            .thenReply(cmd.replyTo(), (s) -> new TagVersion.Response.TagSuccessfullyRegistered());
    }

    private ReplyEffect<ACEvent, State> handleUpdateTag(
        final State.ACState state,
        final ACCommand.UpdateArtifactTag cmd
    ) {
        return this.Effect()
            .persist(new ACEvent.ArtifactTagRegistered(state.coordinates(), cmd.entry()))
            .thenReply(cmd.replyTo(), (s) -> new TagRegistration.Response.TagSuccessfullyRegistered());
    }

}
