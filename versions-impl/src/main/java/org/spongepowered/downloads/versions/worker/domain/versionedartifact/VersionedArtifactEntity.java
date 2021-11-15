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
import com.lightbend.lagom.javadsl.persistence.AkkaTaggerAdapter;
import org.spongepowered.downloads.versions.util.jgit.CommitResolver;
import org.spongepowered.downloads.versions.worker.actor.artifacts.FileCollectionOperator;
import org.spongepowered.downloads.versions.worker.actor.artifacts.PotentiallyUsableAsset;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class VersionedArtifactEntity
    extends EventSourcedBehaviorWithEnforcedReplies<VersionedArtifactCommand, ArtifactEvent, ArtifactState> {
    public static EntityTypeKey<VersionedArtifactCommand> ENTITY_TYPE_KEY = EntityTypeKey.create(
        VersionedArtifactCommand.class, "VersionedArtifactEntity");
    private final Function<ArtifactEvent, Set<String>> tagger;
    private final ActorRef<FileCollectionOperator.Request> scanFiles;
    private final ActorRef<CommitResolver.Command> resolverRef;
    private final ActorContext<VersionedArtifactCommand> ctx;

    public static Behavior<VersionedArtifactCommand> create(final EntityContext<VersionedArtifactCommand> context) {
        return Behaviors.setup(ctx -> {
            final var scanFiles = Routers.group(FileCollectionOperator.KEY);
            final var scanRef = ctx.spawn(scanFiles, "scan-files-" + context.getEntityId());
            final var resolveCommit = Routers.group(CommitResolver.SERVICE_KEY);
            final var resolverRef = ctx.spawn(resolveCommit, "resolve-commit-" + context.getEntityId());
            return new VersionedArtifactEntity(context, ctx, scanRef, resolverRef);
        });
    }

    private VersionedArtifactEntity(
        final EntityContext<VersionedArtifactCommand> entityContext,
        final ActorContext<VersionedArtifactCommand> ctx,
        final ActorRef<FileCollectionOperator.Request> scanRef,
        final ActorRef<CommitResolver.Command> resolverRef
    ) {
        super(PersistenceId.of(entityContext.getEntityTypeKey().name(), entityContext.getEntityId()));
        this.ctx = ctx;
        this.tagger = AkkaTaggerAdapter.fromLagom(entityContext, ArtifactEvent.INSTANCE);
        this.scanFiles = scanRef;
        this.resolverRef = resolverRef;
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
            .onCommand(VersionedArtifactCommand.RegisterRepo.class, this::registerRepoFirst)
            ;
        builder.forStateType(ArtifactState.Registered.class)
            .onCommand(VersionedArtifactCommand.Register.class, cmd -> this.Effect().reply(cmd.replyTo(), Done.done()))
            .onCommand(VersionedArtifactCommand.AddAssets.class, this::onAddAssets)
            .onCommand(VersionedArtifactCommand.MarkFilesAsErrored.class, this::onMarkFilesAsErrored)
            .onCommand(VersionedArtifactCommand.RegisterRawCommit.class, this::onRegisterRawCommit)
            .onCommand(VersionedArtifactCommand.RegisterRepo.class, this::handleRegisterRepo)
            .onCommand(VersionedArtifactCommand.RegisterResolvedCommit.class, this::handleCompletedCommit)
            ;
        return builder.build();
    }

    private ReplyEffect<ArtifactEvent, ArtifactState> handleCompletedCommit(
        final ArtifactState.Registered state, final VersionedArtifactCommand.RegisterResolvedCommit cmd
    ) {
        if (state.fileStatus().commit().isPresent()) {
            return this.Effect().reply(cmd.replyTo(), Done.done());
        }
        return this.Effect()
            .persist(new ArtifactEvent.CommitResolved(state.coordinates(), cmd.repo(), cmd.versionedCommit()))
            .thenReply(cmd.replyTo(), ns -> Done.done());
    }

    private ReplyEffect<ArtifactEvent, ArtifactState> registerRepoFirst(final VersionedArtifactCommand.RegisterRepo cmd) {
        return this.Effect()
            .persist(Arrays.asList(new ArtifactEvent.Registered(cmd.second()), new ArtifactEvent.RepositoryRegistered(cmd.repository())))
            .thenReply(cmd.replyTo(), ns -> Done.done());
    }

    private ReplyEffect<ArtifactEvent, ArtifactState> handleRegisterRepo(
        final ArtifactState.Registered state, final VersionedArtifactCommand.RegisterRepo cmd
    ) {
        if (state.repo().contains(cmd.repository())) {
            return this.Effect()
                .reply(cmd.replyTo(), Done.done());
        }
        return this.Effect()
            .persist(new ArtifactEvent.RepositoryRegistered(cmd.repository()))
            .thenRun(ns -> {
                final var sha = ns.commitSha();
                if (sha.isPresent() && ns.needsCommitResolution()) {
                    this.resolverRef.tell(new CommitResolver.ResolveCommitDetails(
                        state.coordinates(),
                        sha.get(),
                        ns.repositories(),
                        this.ctx.getSystem().ignoreRef()
                    ));
                }
            })
            .thenReply(cmd.replyTo(), ns -> Done.done());
    }

    private ReplyEffect<ArtifactEvent, ArtifactState> onRegisterRawCommit(
        final ArtifactState.Registered state,
        final VersionedArtifactCommand.RegisterRawCommit cmd
        ) {
        return this.Effect()
            .persist(state.associateCommit(cmd.commitSha()))
            .thenRun(ns -> {
                if (ns.needsCommitResolution()) {
                    this.resolverRef.tell(new CommitResolver.ResolveCommitDetails(
                        state.coordinates(),
                        cmd.commitSha(),
                        ns.repositories(),
                        this.ctx.getSystem().ignoreRef()
                    ));
                }
            })
            .thenNoReply();
    }

    private ReplyEffect<ArtifactEvent, ArtifactState> onMarkFilesAsErrored() {
        return this.Effect()
            .persist(new ArtifactEvent.FilesErrored())
            .thenNoReply();
    }

    private ReplyEffect<ArtifactEvent, ArtifactState> onAddAssets(
        final ArtifactState.Registered state,
        final VersionedArtifactCommand.AddAssets cmd
    ) {
        return this.Effect()
            .persist(state.addAssets(cmd))
            .thenRun(ns -> {
                this.ctx.getLog().info("Updated assets for {}", state.coordinates());
                if (state.needsArtifactScan()) {
                    final var usableAssets = ns.artifacts()
                        .filter(a -> "jar".equalsIgnoreCase(a.extension()))
                        .filter(a -> a.classifier().filter(Predicate.not("sources"::equalsIgnoreCase)).isPresent())
                        .map(a -> new PotentiallyUsableAsset(state.coordinates(), a.extension(), a.downloadUrl()));
                    this.scanFiles.tell(new FileCollectionOperator.TryFindingCommitForFiles(usableAssets, state.coordinates()));
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
}
