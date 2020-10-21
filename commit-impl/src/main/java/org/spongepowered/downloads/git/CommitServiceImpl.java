package org.spongepowered.downloads.git;

import akka.NotUsed;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import io.vavr.collection.List;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.spongepowered.downloads.git.api.Commit;
import org.spongepowered.downloads.git.api.CommitDiff;
import org.spongepowered.downloads.git.api.CommitService;
import org.spongepowered.downloads.git.api.Repository;
import org.spongepowered.downloads.git.api.RepositoryRegistration;
import org.spongepowered.downloads.utils.UUIDType5;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CommitServiceImpl implements CommitService {

    private static final String ENTITY_KEY = CommitServiceImpl.class.getName();
    private static final Logger LOGGER = LogManager.getLogger(CommitServiceImpl.class);

    private final PersistentEntityRegistry registry;

    @Inject
    public CommitServiceImpl(
        final PersistentEntityRegistry registry
    ) {
        this.registry = registry;
        this.registry.register(CommitEntity.class);
    }

    @Override
    public ServiceCall<CommitDiff, List<Commit>> getGitDiff(final String repo) {
        return (diff) -> this.getCommitEntity()
            .ask(new CommitCommand.GetCommitsBetween(repo, diff));
    }

    @Override
    public ServiceCall<RepositoryRegistration, Repository> registerRepository() {
        return (registration) -> {
            final UUID uuid = UUIDType5.nameUUIDFromNamespaceAndBytes(UUIDType5.NAMESPACE_OID, registration.name.getBytes());
            return this.getCommitEntity()
                .ask(new CommitCommand.RegisterRepositoryCommand(registration, uuid));
        };
    }

    @Override
    public ServiceCall<NotUsed, Optional<Commit>> getCommit(final String repo, final String commit) {
        return null;
    }

    private PersistentEntityRef<CommitCommand> getCommitEntity() {
        return this.registry.refFor(CommitEntity.class, CommitServiceImpl.ENTITY_KEY);
    }

}
