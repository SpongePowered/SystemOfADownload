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
package org.spongepowered.downloads.git;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import io.vavr.collection.List;
import io.vavr.collection.TreeMultimap;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.spongepowered.downloads.git.api.Commit;
import org.spongepowered.downloads.git.api.Repository;
import org.spongepowered.downloads.git.utils.JgitCommitToApiCommit;

import java.util.Optional;

@SuppressWarnings("unchecked")
public final class CommitEntity extends PersistentEntity<CommitCommand, CommitEvent, CommitsState> {
    private static final Logger LOGGER = LogManager.getLogger(CommitEntity.class);

    @SuppressWarnings("unchecked")
    @Override
    public Behavior initialBehavior(final Optional<CommitsState> snapshotState) {
        final BehaviorBuilder builder = this.newBehaviorBuilder(
            snapshotState.orElseGet(CommitsState::empty));
        // Repository registration
        builder.setCommandHandler(CommitCommand.RegisterRepositoryCommand.class, this::registerRepository);
        builder.setEventHandler(CommitEvent.GitRepoRegistered.class, this::handleGitRepoRegistered);

        // Commit Creation/Management
        builder.setCommandHandler(CommitCommand.GetCommitsBetween.class, this::calculateDiffBetween);
        return builder.build();
    }

    private CommitsState handleGitRepoRegistered(final CommitEvent.GitRepoRegistered event) {
        final CommitsState state = this.state();
        final var newCommitsMap = state.repositoryCommits.put(
            event.repository(),
            TreeMultimap.withSeq().empty()
        );

        final CommitsState.Builder stateBuilder = new CommitsState.Builder()
            .repositories(newCommitsMap.keySet());

        newCommitsMap.forEach(stateBuilder::repositoryBranchCommits);

        return stateBuilder.build();
    }

    private Persist<? extends CommitEvent> registerRepository(
        final CommitCommand.RegisterRepositoryCommand registerRepo,
        final CommandContext<Repository> ctx
    ) {
        if (this.state().repositories.containsKey(registerRepo.generatedId())) {
            ctx.invalidCommand("Repository already registered");
            return ctx.done();
        }
        LOGGER.debug("Registering Repository");
        final var registration = registerRepo.repositoryRegistration();
        final var repository = new Repository.Builder()
            .setId(registerRepo.generatedId())
            .setName(registration.name)
            .setRepoUrl(registration.gitUrl)
            .setWebsite(registration.website)
            .build();

        final var event = new CommitEvent.GitRepoRegistered(repository);
        return ctx.thenPersistAll(java.util.List.of(event), () -> ctx.reply(repository));
    }

    private Persist<? extends CommitEvent> calculateDiffBetween(
        final CommitCommand.GetCommitsBetween commitsRequest,
        final CommandContext<List<Commit>> ctx
    ) {
        return this.state().repositoryByName
            .get(commitsRequest.repo())
            .map(repo -> {
                // This does not perform any caching or lookup, this always clones the repository
                // with a temporary directory. We can do a few things with this: caching in memory
                // starting with the commit sha(s) joined together by `:` as the string key,
                // or store the diffs into state, but that may be expensive to do and linearly
                // increases with different requests.
                try {
                    final var createdCommits = JgitCommitToApiCommit.getCommitsFromRepo(commitsRequest, repo);
                    return ctx.thenPersistAll(
                        // Events
                        createdCommits.map(CommitEvent.CommitCreated::new).asJava(),
                        // And then finally return the commits
                        () -> ctx.reply(createdCommits)
                    );
                } catch (final Exception e) {
                    ctx.commandFailed(e);
                    return ctx.done();
                }
            }).getOrElse(() -> {
                ctx.invalidCommand("Repository is not registered");
                return ctx.done();
            });
    }

}
