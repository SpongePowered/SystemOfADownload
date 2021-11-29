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
package org.spongepowered.downloads.versions.worker.domain.versionedartifact;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Routers;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;
import akka.persistence.typed.javadsl.RetentionCriteria;
import com.lightbend.lagom.javadsl.persistence.AkkaTaggerAdapter;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.versions.worker.actor.artifacts.FileCollectionOperator;
import org.spongepowered.downloads.versions.worker.actor.artifacts.PotentiallyUsableAsset;

import java.util.Set;
import java.util.function.Function;

public class VersionedArtifactEntity
    extends EventSourcedBehaviorWithEnforcedReplies<VersionedArtifactCommand, ArtifactEvent, ArtifactState> {
    public static EntityTypeKey<VersionedArtifactCommand> ENTITY_TYPE_KEY = EntityTypeKey.create(
        VersionedArtifactCommand.class, "VersionedArtifactEntity");
    private final Function<ArtifactEvent, Set<String>> tagger;
    private final ActorRef<FileCollectionOperator.Request> scanFiles;
    private final ActorContext<VersionedArtifactCommand> ctx;

    public static Behavior<VersionedArtifactCommand> create(final EntityContext<VersionedArtifactCommand> context) {
        return Behaviors.setup(ctx -> {
            final var scanFiles = Routers.group(FileCollectionOperator.KEY);
            final var scanRef = ctx.spawn(scanFiles, "scan-files-" + context.getEntityId());
            if (ctx.getLog().isTraceEnabled()) {
                ctx.getLog().trace("Entity Context {}", context.getEntityId());
            }
            return new VersionedArtifactEntity(context, ctx, scanRef);
        });
    }

    private VersionedArtifactEntity(
        final EntityContext<VersionedArtifactCommand> entityContext,
        final ActorContext<VersionedArtifactCommand> ctx,
        final ActorRef<FileCollectionOperator.Request> scanRef
    ) {
        super(PersistenceId.of(entityContext.getEntityTypeKey().name(), entityContext.getEntityId()));
        this.ctx = ctx;
        this.tagger = AkkaTaggerAdapter.fromLagom(entityContext, ArtifactEvent.INSTANCE);
        this.scanFiles = scanRef;
    }

    @Override
    public ArtifactState emptyState() {
        return new ArtifactState.Unregistered();
    }

    @Override
    public EventHandler<ArtifactState, ArtifactEvent> eventHandler() {
        final var builder = this.newEventHandlerBuilder();
        builder.forStateType(ArtifactState.Unregistered.class)
            .onEvent(ArtifactEvent.Registered.class, ArtifactState::register)
            ;
        builder.forStateType(ArtifactState.Registered.class)
            .onEvent(ArtifactEvent.AssetsUpdated.class, (s, e) -> s.withAssets(e.artifacts()))
            .onEvent(ArtifactEvent.FilesErrored.class, (s, e) -> s.markFilesErrored())
            .onEvent(ArtifactEvent.CommitAssociated.class, (s, e) -> s.withCommit(e.commitSha()))
            .onEvent(ArtifactEvent.CommitResolved.class, ArtifactState.Registered::resolveCommit)
            ;
        return builder.build();
    }

    @Override
    public CommandHandlerWithReply<VersionedArtifactCommand, ArtifactEvent, ArtifactState> commandHandler() {
        final var builder = this.newCommandHandlerWithReplyBuilder();
        builder.forStateType(ArtifactState.Unregistered.class)
            .onCommand(VersionedArtifactCommand.Register.class, this::onRegister)
            ;
        builder.forStateType(ArtifactState.Registered.class)
            .onCommand(VersionedArtifactCommand.Register.class, cmd -> this.Effect().reply(cmd.replyTo(), Done.done()))
            .onCommand(VersionedArtifactCommand.AddAssets.class, this::onAddAssets)
            .onCommand(VersionedArtifactCommand.MarkFilesAsErrored.class, this::onMarkFilesAsErrored)
            .onCommand(VersionedArtifactCommand.RegisterRawCommit.class, this::onRegisterRawCommit)
            .onCommand(VersionedArtifactCommand.RegisterResolvedCommit.class, this::handleCompletedCommit)
            ;
        return builder.build();
    }

    private ReplyEffect<ArtifactEvent, ArtifactState> handleCompletedCommit(
        final ArtifactState.Registered state, final VersionedArtifactCommand.RegisterResolvedCommit cmd
    ) {
        this.ctx.getLog().info("Received completed commit {}", cmd.versionedCommit());
        if (state.fileStatus().commit().isPresent()) {
            return this.Effect().reply(cmd.replyTo(), Done.done());
        }
        return this.Effect()
            .persist(new ArtifactEvent.CommitResolved(state.coordinates(), cmd.repo(), cmd.versionedCommit()))
            .thenReply(cmd.replyTo(), ns -> Done.done());
    }

    private ReplyEffect<ArtifactEvent, ArtifactState> onRegisterRawCommit(
        final ArtifactState.Registered state,
        final VersionedArtifactCommand.RegisterRawCommit cmd
        ) {
        this.ctx.getLog().debug("Raw commit registered {}", cmd);
        return this.Effect()
            .persist(state.associateCommit(cmd.commitSha()))
            .thenNoReply();
    }

    private ReplyEffect<ArtifactEvent, ArtifactState> onMarkFilesAsErrored() {
        return this.Effect()
            .persist(new ArtifactEvent.FilesErrored())
            .thenRun(ns -> this.ctx.getLog().debug("File as failed {}", ns))
            .thenNoReply();
    }

    private ReplyEffect<ArtifactEvent, ArtifactState> onAddAssets(
        final ArtifactState.Registered state,
        final VersionedArtifactCommand.AddAssets cmd
    ) {
        if (this.ctx.getLog().isTraceEnabled()) {
            this.ctx.getLog().trace("[{}] Current assets: {}", state.coordinates(), state.artifacts());
            this.ctx.getLog().trace("[{}] Adding assets {}", state.coordinates(), cmd.artifacts());
        }
        return this.Effect()
            .persist(state.addAssets(cmd))
            .thenRun(ns -> {
                if (this.ctx.getLog().isTraceEnabled()) {
                    this.ctx.getLog().trace("[{}] Updated assets", state.coordinates());
                }
                if (ns.needsArtifactScan()) {
                    if (this.ctx.getLog().isDebugEnabled()) {
                        this.ctx.getLog().debug(
                            "[{}] Telling FileCollectionOperator to fetch commit from files",
                            state.coordinates()
                        );
                    }
                    if (this.ctx.getLog().isTraceEnabled()) {
                        this.ctx.getLog().trace(
                            "[{}] Assets available {}", state.coordinates(), ns.artifacts().map(Artifact::toString));
                    }
                    final var usableAssets = ns.artifacts()
                        .filter(a -> "jar".equalsIgnoreCase(a.extension()))
                        .filter(a -> a.classifier().isEmpty() || a.classifier().filter(""::equals).isPresent())
                        .map(a -> new PotentiallyUsableAsset(state.coordinates(), a.extension(), a.downloadUrl()));
                    if (!usableAssets.isEmpty()) {
                        if (this.ctx.getLog().isDebugEnabled()) {
                            this.ctx.getLog().debug("[{}] Assets to scan {}", state.coordinates(), usableAssets);
                        }
                        this.scanFiles.tell(
                            new FileCollectionOperator.TryFindingCommitForFiles(usableAssets, state.coordinates()));
                    }
                }
            })
            .thenReply(cmd.replyTo(), ns -> Done.done());
    }

    private ReplyEffect<ArtifactEvent, ArtifactState> onRegister(
        final ArtifactState.Unregistered state,
        final VersionedArtifactCommand.Register cmd
    ) {
        return this.Effect()
            .persist(state.register(cmd))
            .thenReply(cmd.replyTo(), ns -> Done.done());
    }


    @Override
    public Set<String> tagsFor(final ArtifactEvent assetEvent) {
        return this.tagger.apply(assetEvent);
    }

    @Override
    public RetentionCriteria retentionCriteria() {
        return RetentionCriteria.snapshotEvery(5, 2);
    }
}
