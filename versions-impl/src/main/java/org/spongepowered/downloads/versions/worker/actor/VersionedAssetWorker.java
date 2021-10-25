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
package org.spongepowered.downloads.versions.worker.actor;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Routers;
import akka.actor.typed.receptionist.ServiceKey;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.japi.function.Function;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorFlow;
import io.vavr.control.Try;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.worker.VersionConfig;
import org.spongepowered.downloads.versions.worker.domain.GitBasedArtifact;
import org.spongepowered.downloads.versions.worker.domain.GitCommand;
import org.spongepowered.downloads.versions.worker.domain.RepositoryCommand;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;

/**
 * purpose:
 * Given a list of asset URLs, collect the subset of URLs corresponding to assets whose manifests have a 'git.commit'
 * key
 * and return the value of that key.
 */
public final class VersionedAssetWorker {

    public static final ServiceKey<Command> SERVICE_KEY = ServiceKey.create(Command.class, "versioned-asset-commit-fetcher");

    /**
     * Creates the {@link Behavior} that accepts {@link Command Commands} as part
     * of a saga, either kicking off with {@link FetchCommitFromAsset} for valid
     * {@link ArtifactCollection ArtifactCollections}, or an {@link IgnoredUpdate}.
     * The worker performs a saga process to verify if the versioned asset has a
     * commit sha available, and if so, performs a registration of said
     * commit against the artifact version.
     *
     * @param config The config, mostly to configure pooling
     * @return The stateless Actor behavior to process
     */
    public static Behavior<Command> commitFetcher(VersionConfig.CommitFetch config) {
        return Behaviors.setup(ctx -> {
            final var sharding = ClusterSharding.get(ctx.getSystem());
            final var commitExtractorRouter = Routers.group(CommitExtractor.SERVICE_KEY);
            final var fileHandler = ctx.spawn(commitExtractorRouter, "commit-extractor");
            return Behaviors.receive(Command.class)
                .onMessage(IgnoredUpdate.class, cmd -> {
                    cmd.replyTo().tell(Done.done());
                    return Behaviors.same();
                })
                .onMessage(FetchCommitFromAsset.class, requestGitRepoForAsset(config, ctx, sharding))
                .onMessage(TerminatingCommand.class, cmd -> {
                    // Effectively, some commands are terminating with their
                    // origins already logging in error cases
                    cmd.replyTo().tell(Done.done());
                    return Behaviors.same();
                })
                .onMessage(RepoAvailable.class, fanOutAssetsForCommitRetrieval(ctx, fileHandler))
                .onMessage(RepoNotRegistered.class, cmd -> {
                    ctx.getLog().info("Git repository not registered");
                    cmd.replyTo.tell(Done.done());
                    return Behaviors.same();
                })
                .onMessage(CommitRetrievedFromAsset.class, registerCommitWithArtifactVersion(ctx, sharding))
                .build();
        });
    }

    /**
     * Base interface of commands available for {@link VersionedAssetWorker}.
     * Public commands include:
     * <ul>
     *     <li>{@link FetchCommitFromAsset}</li>
     *     <li>{@link IgnoredUpdate}</li>
     * </ul>
     * Other commands are inner saga step commands that ultimately respond with
     * a {@link Done} instance that was passed in usually from
     * {@link FetchCommitFromAsset#replyTo()}.
     */
    public interface Command {
    }

    /**
     * Starting command for {@link VersionedAssetWorker}, this is passed the
     * {@link ArtifactCollection} which should contain all assets that have
     * a valid {@link Artifact#downloadUrl()} to be able to work with. Likewise,
     * contains the {@link ActorRef} to reply a {@link Done}.
     */
    public static record
    FetchCommitFromAsset(
        ArtifactCollection collection, ActorRef<Done> replyTo
    ) implements Command {

    }

    /**
     * A simple ignoring command that ought to reply fast with a {@link Done}
     * response. This is used to acknowledge messages that are otherwise not
     * needing "work to be done" by the {@link VersionedAssetWorker}.
     */
    public static record IgnoredUpdate(ActorRef<Done> replyTo) implements Command {

    }

    private static Function<FetchCommitFromAsset, Behavior<Command>> requestGitRepoForAsset(
        VersionConfig.CommitFetch config, ActorContext<Command> ctx, ClusterSharding sharding
    ) {
        return cmd -> {
            ctx.getLog().debug("Received request for fetching commits for artifact {} from artifacts {}", cmd.collection.coordinates(), cmd.collection.components().map(Artifact::classifier));
            // Start the kickoff of work to be done by requesting
            // from the EventSourced Entity for a GitRepo if it has one
            final var coordinates = cmd.collection.coordinates();
            final var ref = sharding.entityRefFor(
                GitBasedArtifact.ENTITY_TYPE_KEY,
                coordinates.asArtifactCoordinates().asMavenString()
            );
            ctx.pipeToSelf(
                ref.ask(GitCommand.GetGitRepo::new, config.timeout),
                (response, throwable) -> {
                    if (throwable != null) {
                        ctx.getLog().warn("Received throwable during git repo request for " + cmd.collection.coordinates(), throwable);
                        return new RepoNotRegistered(cmd.replyTo());
                    }
                    if (response instanceof RepositoryCommand.Response.RepositoryAvailable available) {
                        return new RepoAvailable(available.repo(), cmd.collection(), cmd.replyTo());
                    }
                    ctx.getLog().warn("Received different response for {} than expected: {}", cmd.collection.coordinates(), response);
                    return new RepoNotRegistered(cmd.replyTo());
                }
            );
            return Behaviors.same();
        };
    }

    /**
     * Gathers the available assets from {@link RepoAvailable#collection()} and
     * performs a fan-out of {@link PotentiallyUsableAsset}s with a child actor
     * {@link CommitExtractor#extractCommitFromAssets()}. The extractor reports
     * back for each file with a {@link CommitExtractor.AssetCommitResponse} to
     * help filter through files and determine if any assets have a commit sha
     * available.
     *
     * @param ctx The context of this actor
     * @param fileHandler The child reference
     * @return The same behavior
     */
    private static Function<RepoAvailable, Behavior<Command>> fanOutAssetsForCommitRetrieval(
        ActorContext<Command> ctx, ActorRef<CommitExtractor.ChildCommand> fileHandler
    ) {
        return cmd -> {
            ctx.getLog().debug("Repository available, gathering artifacts for {}", cmd.collection);
            // Filter assets for potential jars to get their manifests
            final var optionalUrl = cmd.collection().components()
                .filter(artifact -> "jar".equalsIgnoreCase(artifact.extension()))
                .map(artifact -> new PotentiallyUsableAsset(cmd.collection().coordinates(), artifact.extension(),
                    artifact.downloadUrl()
                ));
            // If we have none, well... we're done.
            if (optionalUrl.isEmpty()) {
                ctx.getLog().debug("url was empty for {}", cmd.collection.coordinates());
                cmd.replyTo().tell(Done.done());
                return Behaviors.same();
            }
            // Otherwise, try sorting the download files by their smallest
            // payload size (avoids trying to download the "fat" jars which
            // can potentially mean we get our information from a sources
            // jar, which weighs roughly some dozen of kilobytes).
            final var smallestOrderedJars = optionalUrl
                .sortBy(asset -> Try.of(
                        () -> HttpRequest.newBuilder(asset.downloadURL())
                            .header("User-Agent", "SystemOfADownload/Asset-Commit-Retriever")
                            .GET()
                            .build()
                    )
                    .mapTry(request -> Try.of(
                            () -> HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding())
                        )
                    )
                    .map(response -> response.map(resp -> resp.headers()
                                .firstValueAsLong("Content-Length")
                            )
                            .getOrElse(OptionalLong.empty())
                            .orElse(Long.MAX_VALUE)
                    ).getOrElse(Long.MAX_VALUE));
            ctx.getLog().debug("Smallest ordered jars are as follows {}", smallestOrderedJars);


            // Create the flow for the child actor that'll handle
            // Streaming the file, viewing the manifest, getting data,
            // then reporting back to us
            final Flow<PotentiallyUsableAsset, CommitExtractor.AssetCommitResponse, NotUsed> fileFetcherFlow = ActorFlow.ask(
                fileHandler, Duration.ofMinutes(1),
                (asset, replyTo) -> new CommitExtractor.AttemptFileCommit(asset, cmd.repo(), replyTo)
            );
            // Then create the graph of operations and kick it off
            // which can potentially lead to an optional result
            final Source<CommitExtractor.AssetCommitResponse, NotUsed> filter = Source.from(
                    smallestOrderedJars)
                .async()
                .via(fileFetcherFlow)
                .filter(r -> r instanceof CommitExtractor.DiscoveredCommitFromFile);
            final CompletionStage<Optional<CommitExtractor.DiscoveredCommitFromFile>> commitAvailable = filter
                .map(r -> (CommitExtractor.DiscoveredCommitFromFile) r)
                .runWith(Sink.headOption(), ctx.getSystem());

            // Then, go ahead and submit the graph for processing
            ctx.pipeToSelf(
                commitAvailable,
                (optionalCommit, throwable) -> {
                    if (throwable != null) {
                        return new CommitResolutionFailed(cmd.collection().coordinates(), cmd.replyTo());
                    }
                    return optionalCommit
                        .<Command>map(
                            commit -> new CommitRetrievedFromAsset(commit.sha(), cmd.collection().coordinates(),
                                cmd.replyTo()
                            ))
                        .orElseGet(() -> new NoCommitFound(cmd.collection().coordinates(), cmd.replyTo()));
                }
            );
            return Behaviors.same();
        };
    }

    private static Function<CommitRetrievedFromAsset, Behavior<Command>> registerCommitWithArtifactVersion(
        ActorContext<Command> ctx, ClusterSharding sharding
    ) {
        return cmd -> {
            final var associateCommitWithVersion = sharding.entityRefFor(
                    GitBasedArtifact.ENTITY_TYPE_KEY,
                    cmd.coordinates().asArtifactCoordinates().asMavenString()
                )
                .<Done>ask(
                    replyTo -> new GitCommand.AssociateCommitWithVersion(cmd.sha(), cmd.coordinates(), replyTo),
                    Duration.ofMinutes(5)
                )
                .toCompletableFuture();
            ctx.pipeToSelf(associateCommitWithVersion, (done, throwable) -> {
                if (throwable != null) {
                    ctx.getLog().warn("Failed to register commit sha with version", throwable);
                }
                return new WorkCompleted(cmd.replyTo());
            });
            return Behaviors.same();
        };
    }

    private interface TerminatingCommand extends Command {
        ActorRef<Done> replyTo();
    }

    private static record RepoNotRegistered(ActorRef<Done> replyTo) implements TerminatingCommand {

    }

    private static record RepoAvailable(
        String repo,
        ArtifactCollection collection,
        ActorRef<Done> replyTo
    ) implements Command {
    }

    private static record CommitResolutionFailed(MavenCoordinates coordinates, ActorRef<Done> replyTo)
        implements TerminatingCommand {
    }

    private static record CommitRetrievedFromAsset(String sha, MavenCoordinates coordinates, ActorRef<Done> replyTo)
        implements Command {
    }

    private static record NoCommitFound(MavenCoordinates coordinates, ActorRef<Done> replyTo)
        implements TerminatingCommand {
    }

    private static record WorkCompleted(ActorRef<Done> replyTo) implements TerminatingCommand {
    }

    public static record PotentiallyUsableAsset(
        MavenCoordinates mavenCoordinates,
        String coordinates,
        URI downloadURL
    ) {
    }

}