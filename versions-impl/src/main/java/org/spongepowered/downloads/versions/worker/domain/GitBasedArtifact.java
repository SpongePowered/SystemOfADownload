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
package org.spongepowered.downloads.versions.worker.domain;

import akka.Done;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.RetentionCriteria;
import com.lightbend.lagom.javadsl.persistence.AkkaTaggerAdapter;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import org.spongepowered.downloads.versions.server.collection.InvalidRequest;

import java.net.URI;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class GitBasedArtifact extends EventSourcedBehaviorWithEnforcedReplies<GitCommand, GitEvent, GitState> {
    public static EntityTypeKey<GitCommand> ENTITY_TYPE_KEY = EntityTypeKey.create(
        GitCommand.class, "GitBasedArtifact");
    private final Function<GitEvent, Set<String>> tagger;
    private final ActorContext<GitCommand> ctx;

    public static Behavior<GitCommand> create(final EntityContext<GitCommand> context) {
        return Behaviors.setup(ctx -> new GitBasedArtifact(context, ctx));
    }

    private GitBasedArtifact(
        final EntityContext<GitCommand> context,
        final ActorContext<GitCommand> ctx
    ) {
        super(
            // PersistenceId needs a typeHint (or namespace) and entityId,
            // we take then from the EntityContext
            PersistenceId.of(
                context.getEntityTypeKey().name(), // <- type hint
                context.getEntityId() // <- business id
            ));
        this.tagger = AkkaTaggerAdapter.fromLagom(context, GitEvent.INSTANCE);
        this.ctx = ctx;
    }

    @Override
    public GitState emptyState() {
        return GitState.empty();
    }

    @Override
    public EventHandler<GitState, GitEvent> eventHandler() {
        final var builder = this.newEventHandlerBuilder();
        builder.forStateType(GitState.Empty.class)
            .onEvent(GitEvent.RepoRegistered.class, GitState::empty)
            .onEvent(GitEvent.CommitAssociatedWithVersion.class, GitState::empty)
            .onEvent(GitEvent.ArtifactRegistered.class, e -> new GitState.Registered(e.coordinates(), HashSet.empty()));
        builder.forStateType(GitState.Registered.class)
            .onEvent(
                GitEvent.RepoRegistered.class,
                (s, e) -> new GitState.RepositoryAssociated(
                    s.coordinates(), HashSet.of(e.repository()), HashMap.empty())
            )
            .onEvent(
                GitEvent.VersionRegistered.class,
                (s, e) -> s.acceptVersion(e.coordinates())
            )
            .onEvent(GitEvent.CommitAssociatedWithVersion.class, (s, e) -> s)
            .onEvent(GitEvent.ArtifactRegistered.class, (s, e) -> s);
        builder.forStateType(GitState.RepositoryAssociated.class)
            .onEvent(
                GitEvent.RepoRegistered.class,
                (s, e) -> new GitState.RepositoryAssociated(
                    s.coordinates(), HashSet.of(e.repository()), s.commits())
            )
            .onEvent(
                GitEvent.VersionRegistered.class,
                GitState.RepositoryAssociated::addVersion
            )
            .onEvent(GitEvent.ArtifactRegistered.class, (s, e) -> s)
            .onEvent(GitEvent.CommitAssociatedWithVersion.class, GitState.RepositoryAssociated::appendCommitToVersion)
            .onEvent(GitEvent.ArtifactLabeledMissingCommit.class, GitState.RepositoryAssociated::labelAssetsAsMissingCommit)
            .onEvent(GitEvent.CommitDetailsUpdated.class, GitState.RepositoryAssociated::associateCommitDetails)
        ;
        return builder.build();
    }

    @Override
    public CommandHandlerWithReply<GitCommand, GitEvent, GitState> commandHandler() {
        final var builder = this.newCommandHandlerWithReplyBuilder();
        builder.forStateType(GitState.Empty.class)
            .onCommand(
                GitCommand.RegisterArtifact.class,
                (s, cmd) -> this.Effect()
                    .persist(new GitEvent.ArtifactRegistered(cmd.coordinates()))
                    .thenReply(cmd.replyTo(), ns -> Done.done())
            )
            .onCommand(
                GitCommand.AssociateCommitWithVersion.class,
                (state, cmd) -> this.Effect().reply(cmd.replyTo(), Done.done())
            ).onCommand(
                GitCommand.RegisterVersion.class,
                (state, cmd) -> this.Effect().reply(cmd.replyTo(), Done.done())
            ).onCommand(
                GitCommand.RegisterRepository.class,
                (state, cmd) -> this.Effect().reply(cmd.replyTo(), Done.done())
            )
            .onCommand(
                GitCommand.GetGitRepo.class,
                (s, cmd) -> this.Effect().reply(cmd.replyTo(), new InvalidRequest())
            )
            .onCommand(
                GitCommand.CheckIfWorkIsNeeded.class,
                (s, cmd) -> this.Effect().reply(cmd.replyTo(), new InvalidRequest())
            )
            .onCommand(
                GitCommand.GetUnCommittedVersions.class,
                (s, cmd) -> this.Effect().reply(cmd.reply(), List.empty())
            ).onCommand(
                GitCommand.NotifyCommitMissingFromAssets.class,
                (s, cmd) -> this.Effect().reply(cmd.replyTo(), Done.done())
            ).onCommand(
                GitCommand.AssociateCommitDetailsForVersion.class,
                (s, cmd)  -> this.Effect().reply(cmd.replyTo(), Done.done())
            )
        ;
        builder.forStateType(GitState.Registered.class)
            .onCommand(
                GitCommand.RegisterRepository.class,
                (state, cmd) -> this.Effect()
                    .persist(new GitEvent.RepoRegistered(state.coordinates(), URI.create(cmd.repo())))
                    .thenReply(cmd.replyTo(), s -> Done.done())
            )
            .onCommand(
                GitCommand.RegisterArtifact.class,
                (state, cmd) -> this.Effect().reply(cmd.replyTo(), Done.done())
            )
            .onCommand(
                GitCommand.RegisterVersion.class,
                (state, cmd) -> this.Effect()
                    .persist(new GitEvent.VersionRegistered(cmd.coordinates()))
                    .thenReply(cmd.replyTo(), s -> Done.done())
            )
            .onCommand(
                GitCommand.AssociateCommitWithVersion.class,
                (state, cmd) -> this.Effect().reply(cmd.replyTo(), Done.done())
            )
            .onCommand(
                GitCommand.GetGitRepo.class,
                (s, cmd) -> this.Effect().reply(cmd.replyTo(), new InvalidRequest())
            )
            .onCommand(
                GitCommand.CheckIfWorkIsNeeded.class,
                (s, cmd) -> this.Effect().reply(cmd.replyTo(), new InvalidRequest())
            )
            .onCommand(
                GitCommand.GetUnCommittedVersions.class,
                (s, cmd) -> this.Effect().reply(cmd.reply(), List.empty())
            ).onCommand(
                GitCommand.NotifyCommitMissingFromAssets.class,
                (s, cmd) -> this.Effect().reply(cmd.replyTo(), Done.done())
            ).onCommand(
                GitCommand.AssociateCommitDetailsForVersion.class,
                (s, cmd)  -> this.Effect().reply(cmd.replyTo(), Done.done())
            )
        ;
        builder.forStateType(GitState.RepositoryAssociated.class)
            .onCommand(
                GitCommand.RegisterVersion.class,
                (state, cmd) -> this.Effect().persist(new GitEvent.VersionRegistered(cmd.coordinates()))
                    .thenReply(cmd.replyTo(), s -> Done.done())
            )
            .onCommand(
                GitCommand.AssociateCommitWithVersion.class,
                (state, cmd) -> this.Effect()
                    .persist(new GitEvent.CommitAssociatedWithVersion(cmd.sha(), state.gitRepository(), cmd.coordinates()))
                    .thenReply(cmd.replyTo(), s -> Done.done())
            )
            .onCommand(
                GitCommand.RegisterRepository.class,
                (state, cmd) -> this.Effect()
                    .persist(new GitEvent.RepoRegistered(state.coordinates(), URI.create(cmd.repo())))
                    .thenReply(cmd.replyTo(), s -> Done.done())
            )
            .onCommand(
                GitCommand.GetGitRepo.class,
                (s, cmd) -> this.Effect().reply(cmd.replyTo(), new RepositoryCommand.Response.RepositoryAvailable(s.gitRepository().toString()))
            )
            .onCommand(
                GitCommand.CheckIfWorkIsNeeded.class,
                (s, cmd) -> {
                    if (!s.commits().get(cmd.collection().coordinates().version).getOrElse(GitState.Unchecked::new).isProcessed()) {
                        return this.Effect().reply(cmd.replyTo(), new RepositoryCommand.Response.RepositoryAvailable(s.gitRepository().toString()));
                    }
                    return this.Effect().reply(cmd.replyTo(), new InvalidRequest());
                }
            )
            .onCommand(
                GitCommand.GetUnCommittedVersions.class,
                (s, cmd) -> {
                    final var unresolvedMavenVersions = s.commits()
                        .filterValues(Predicate.not(GitState.CommitState::isProcessed))
                        .take(10)
                        .map(t -> s.coordinates().version(t._1))
                        .collect(List.collector());
                    return this.Effect().reply(cmd.reply(), unresolvedMavenVersions);
                }
            ).onCommand(
                GitCommand.NotifyCommitMissingFromAssets.class,
                (s, cmd) -> this.Effect()
                    .persist(new GitEvent.ArtifactLabeledMissingCommit(cmd.coordinates()))
                    .thenReply(cmd.replyTo(), (s2) -> Done.done())
            ).onCommand(
                GitCommand.AssociateCommitDetailsForVersion.class,
                (s, cmd)  -> {
                    this.ctx.getLog().debug("[{}] Got git details {}", cmd.coordinates(), cmd.commit());
                    return this.Effect()
                        .persist(new GitEvent.CommitDetailsUpdated(cmd.coordinates(), cmd.repo(), cmd.commit()))
                        .thenReply(cmd.replyTo(), (ns) -> Done.done());
                }
            )
        ;
        return builder.build();
    }

    @Override
    public Set<String> tagsFor(final GitEvent gitEvent) {
        return this.tagger.apply(gitEvent);
    }

    @Override
    public RetentionCriteria retentionCriteria() {
        return RetentionCriteria.snapshotEvery(50, 2);
    }
}
