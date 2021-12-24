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
package org.spongepowered.synchronizer.gitmanaged.domain;

import akka.Done;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.RetentionCriteria;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

public class GitManagedArtifact extends EventSourcedBehaviorWithEnforcedReplies<GitCommand, GitEvent, GitState> {

    public static final EntityTypeKey<GitCommand> ENTITY_TYPE_KEY = EntityTypeKey.create(
        GitCommand.class, "git-managed-artifact");

    private final ActorContext<GitCommand> ctx;
    private final ArtifactCoordinates coordinates;

    public static Behavior<GitCommand> create(final PersistenceId persistenceId, final String entityId) {
        return Behaviors.setup(ctx -> new GitManagedArtifact(persistenceId, entityId, ctx));
    }

    private GitManagedArtifact(
        final PersistenceId persistenceId,
        final String entityId, final ActorContext<GitCommand> ctx
    ) {
        super(persistenceId);
        final var split = entityId.split(":");
        this.coordinates = new ArtifactCoordinates(split[0], split[1]);
        this.ctx = ctx;
    }

    @Override
    public GitState emptyState() {
        return new GitState.Empty();
    }

    @Override
    public CommandHandlerWithReply<GitCommand, GitEvent, GitState> commandHandler() {
        final var builder = this.newCommandHandlerWithReplyBuilder();
        builder.forAnyState()
            .onCommand(GitCommand.RegisterRepository.class, (state, cmd) -> this.Effect()
                .persist(new GitEvent.RepositoryRegistered(cmd.repository()))
                .thenReply(cmd.replyTo(), ns -> Done.done())
            )
            .onCommand(GitCommand.GetRepositories.class, (state, cmd) -> this.Effect()
                .reply(cmd.replyTo(), state.repositories().isEmpty() ?
                    new GitCommand.NoRepositories() : new GitCommand.RepositoriesAvaiable(state.repositories()))
            )
            .onCommand(GitCommand.GetUnresolvedVersions.class, (state, cmd) -> this.Effect()
                .reply(cmd.replyTo(), state.unresolvedVersions())
            )
            .onCommand(GitCommand.MarkVersionAsResolved.class, (state, cmd) -> this.Effect()
                .persist(new GitEvent.CommitResolved(cmd.coordinates(), cmd.commit()))
                .thenReply(cmd.replyTo(), ns -> Done.done())
            )
            .onCommand(GitCommand.RegisterRawCommit.class, (state, cmd) -> this.Effect()
                .persist(new GitEvent.CommitRegistered(cmd.coordinates(), cmd.commitSha()))
                .thenReply(cmd.replyTo(), ns -> Done.done())
            )
        ;
        return builder.build();
    }

    @Override
    public EventHandler<GitState, GitEvent> eventHandler() {
        final var builder = this.newEventHandlerBuilder();
        builder.forAnyState()
            .onEvent(
                GitEvent.RepositoryRegistered.class,
                (state, event) -> state.withRepository(event.repository())
            )
            .onEvent(
                GitEvent.CommitResolved.class,
                (state, event) -> state.withResolvedVersion(event.coordinates(), event.resolvedCommit())
            )
            .onEvent(
                GitEvent.CommitRegistered.class,
                (state, event) -> state.withRawCommit(event.coordinates(), event.commit())
            )
        ;
        return builder.build();
    }

    @Override
    public RetentionCriteria retentionCriteria() {
        return RetentionCriteria.snapshotEvery(10, 2);
    }
}
