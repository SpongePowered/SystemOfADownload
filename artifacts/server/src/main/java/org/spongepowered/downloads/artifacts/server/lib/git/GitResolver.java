package org.spongepowered.downloads.artifacts.server.lib.git;

import io.vavr.control.Either;
import io.vavr.control.Try;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.spongepowered.downloads.artifact.api.query.ArtifactDetails;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Singleton
public class GitResolver {

    @Inject
    @Named("git-resolver")
    private ExecutorService executorService;

    public CompletableFuture<Either<ArtifactDetails.Response, String>> validateRepository(
        final String repoURL
    ) {
        final var gitLs = CompletableFuture.supplyAsync(() -> Try.of(() -> Git.lsRemoteRepository()
                .setRemote(repoURL)
                .setTags(false)
                .setHeads(false)
                .setTimeout(60)
                .call())
            .toEither()
            .mapLeft(t -> switch (t) {
                case InvalidRemoteException _ -> this.invalidRemote(repoURL);
                case GitAPIException e -> this.genericRemoteProblem(e);
                default -> this.badRequest(repoURL, t);
            }), this.executorService);
        final var validatedReferences = gitLs.thenApply(e -> e.map(refs -> !refs.isEmpty())
            .flatMap(valid -> {
                if (!valid) {
                    return Either.left(this.noReferences(repoURL));
                }
                return Either.right(repoURL);
            }));
        return validatedReferences.toCompletableFuture();
    }

    private ArtifactDetails.Response noReferences(String repoUrl) {
        return  new ArtifactDetails.Response.Error(String.format("Invalid remote: %s. No references found", repoUrl));
    }

    private ArtifactDetails.Response badRequest(String repoURL, Throwable t) {
        return new ArtifactDetails.Response.Error(String.format("Invalid remote: %s. got error %s", repoURL, t));
    }

    private ArtifactDetails.Response invalidRemote(String repoURL) {
        return new ArtifactDetails.Response.Error(String.format("Invalid remote: %s", repoURL));
    }

    private ArtifactDetails.Response genericRemoteProblem(GitAPIException t) {
        return new ArtifactDetails.Response.Error(String.format("Error resolving repository: %s", t));
    }

}


