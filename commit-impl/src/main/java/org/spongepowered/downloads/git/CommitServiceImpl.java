package org.spongepowered.downloads.git;

import akka.NotUsed;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import io.vavr.collection.List;
import org.spongepowered.downloads.git.api.Commit;
import org.spongepowered.downloads.git.api.CommitDiff;
import org.spongepowered.downloads.git.api.CommitService;
import org.spongepowered.downloads.git.api.RepositoryRegistration;

import java.util.Optional;

public class CommitServiceImpl implements CommitService {

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
        return null;
    }

    @Override
    public ServiceCall<RepositoryRegistration, NotUsed> registerRepository() {
        return null;
    }

    @Override
    public ServiceCall<NotUsed, Optional<Commit>> getCommit(final String repo, final String commit) {
        return null;
    }
}
