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
package org.spongepowered.synchronizer.actor;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Routers;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.auth.api.utils.AuthUtils;
import org.spongepowered.downloads.sonatype.AssetSearchResponse;
import org.spongepowered.downloads.sonatype.Component;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.api.models.VersionRegistration;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class VersionedComponentWorker {
    public static final String ASSET_SEARCH_ENDPOINT =
        """
        /service/rest/v1/search/assets?maven.groupId=%s&maven.artifactId=%s&maven.baseVersion=%s&maven.extension=%s
        """.trim();
    public static final String ASSET_SEARCH_WITH_TOKEN =
        """
        /service/rest/v1/search/assets?continuationToken=%s&maven.groupId=%s&maven.artifactId=%s&maven.baseVersion=%s&maven.extension=%s
        """.trim();

    public interface Command {
    }

    public static final record GatherComponentsForArtifact(
        MavenCoordinates coordinates,
        int count, ActorRef<Done> replyTo)
        implements Command {

        public GatherComponentsForArtifact(MavenCoordinates coordinates, ActorRef<Done> replyTo) {
            this(coordinates, 0, replyTo);
        }
    }

    public static final record Ignored(ActorRef<Done> replyTo) implements Command {

    }

    private interface ChildResponse extends Command {
    }

    private static final record ComponentsAvailable(
        MavenCoordinates coordinates,
        List<Component.Asset> assets,
        ActorRef<Done> replyTo
    ) implements ChildResponse {
    }

    private static final record FailedAssetRetrieval(
        MavenCoordinates coordinates,
        int count,
        ActorRef<Done> replyTo
    ) implements ChildResponse {
    }

    public static Behavior<Command> gatherComponents(
        final VersionsService service,
        final ObjectMapper mapper
    ) {
        return Behaviors.setup(ctx -> {
            final AssetSettingsExtension.AssetRetrievalSettings config = AssetSettingsExtension.SettingsProvider.get(
                ctx.getSystem());
            final var assetDispatcher = DispatcherSelector.fromConfig("asset-retrieval-dispatcher");
            final var gathererPool = Routers.pool(config.poolSize, idleFetcher(config, mapper));
            final var gathererRef = ctx.spawn(
                Behaviors.supervise(gathererPool).onFailure(SupervisorStrategy.restart()),
                "search-versioned-components",
                assetDispatcher
            );
            final var auth = AuthUtils.configure(ctx.getSystem().settings().config());
            final var assetRegisters = Routers.pool(config.poolSize, registerAssets(service, auth));
            final var assetRegistersRef = ctx.spawn(
                Behaviors.supervise(assetRegisters).onFailure(SupervisorStrategy.restart()),
                "versioned-asset-register",
                assetDispatcher
            );
            return idleGatherer(gathererRef, assetRegistersRef, config);
        });
    }

    private static Behavior<Command> idleGatherer(
        final ActorRef<Gather> gathererRef,
        final ActorRef<Registrar> assetRegistersRef,
        final AssetSettingsExtension.AssetRetrievalSettings config
    ) {
        return Behaviors.setup(ctx -> Behaviors.receive(Command.class)
            .onMessage(GatherComponentsForArtifact.class, cmd -> {
                config.filesToIndex.forEach(fileType -> ctx.ask(
                    ChildResponse.class,
                    gathererRef,
                    config.timeout,
                    ref -> new StartRequest(cmd.coordinates, fileType, ref, cmd.replyTo),
                    (response, throwable) -> {
                        if (throwable != null) {
                            return new FailedAssetRetrieval(cmd.coordinates, cmd.count + 1, cmd.replyTo);
                        }
                        // In case the child returned a failed asset retrieval
                        if (response instanceof FailedAssetRetrieval f) {
                            return new FailedAssetRetrieval(f.coordinates, f.count + 1, cmd.replyTo);
                        }
                        return response;
                    }
                ));
                return Behaviors.same();
            })
            .onMessage(ComponentsAvailable.class, available -> {
                // now we've gotta register the components to the version service
                available.replyTo.tell(Done.done());
                assetRegistersRef.tell(new AttemptRegistration(available.coordinates, available.assets));
                return Behaviors.same();
            })
            .onMessage(FailedAssetRetrieval.class, failed -> {
                // Retry basically
                ctx.getLog().error("Failed artifact attempt");
                if (failed.count >= config.retryCount) {
                    ctx.getLog().error("Aborting attempt to sync artifact assets");
                    failed.replyTo.tell(Done.done());
                    return Behaviors.same();
                }
                ctx.getSelf().tell(new GatherComponentsForArtifact(failed.coordinates, failed.count, failed.replyTo));
                return Behaviors.same();
            })
            .onMessage(Ignored.class, ignored -> {
                ignored.replyTo.tell(Done.done());
                return Behaviors.same();
            })
            .build());
    }

    private interface Gather {
    }

    private static final record StartRequest(
        MavenCoordinates coordinates,
        String fileType,
        ActorRef<ChildResponse> ref,
        ActorRef<Done> completedReplyTo
    ) implements Gather {
    }

    private static final record ContinueRequest(
        MavenCoordinates coordinates,
        String fileType,
        List<Component.Asset> existing,
        String continuationToken,
        ActorRef<ChildResponse> replyTo,
        ActorRef<Done> completedReplyTo
    ) implements Gather {
    }

    private static final record Completed(
        MavenCoordinates coordinates,
        List<Component.Asset> existing,
        ActorRef<ChildResponse> replyTo,
        ActorRef<Done> completedReplyTo
    ) implements Gather {
    }

    private static final record Failed(
        MavenCoordinates coordinates,
        List<Component.Asset> recovered,
        ActorRef<ChildResponse> replyTo,
        ActorRef<Done> completedReplyTo
    ) implements Gather {
    }

    private static Behavior<Gather> idleFetcher(
        final AssetSettingsExtension.AssetRetrievalSettings config,
        final ObjectMapper playMapper
    ) {
        return Behaviors.setup(ctx -> {
            final var client = HttpClient.newBuilder().connectTimeout(config.timeout)
                .executor(ctx.getExecutionContext())
                .build();
            return Behaviors.receive(Gather.class)
                .onMessage(StartRequest.class, req -> {
                    final var formatted = String.format(
                        config.repository + ASSET_SEARCH_ENDPOINT,
                        req.coordinates.groupId,
                        req.coordinates.artifactId,
                        req.coordinates.version,
                        req.fileType
                    );
                    ctx.pipeToSelf(searchAssets(playMapper, client, formatted), (response, failure) -> {
                        if (failure != null) {
                            return new Failed(req.coordinates, List.empty(), req.ref, req.completedReplyTo);
                        }
                        return response.continuationToken()
                            .<Gather>map(
                                token -> new ContinueRequest(
                                    req.coordinates, req.fileType, response.items(), token, req.ref,
                                    req.completedReplyTo
                                ))
                            .orElseGet(
                                () -> new Completed(req.coordinates, response.items(), req.ref, req.completedReplyTo));
                    });
                    return Behaviors.same();
                })
                .onMessage(ContinueRequest.class, cont -> {
                    final var formatted = String.format(
                        config.repository + ASSET_SEARCH_WITH_TOKEN,
                        cont.continuationToken,
                        cont.coordinates.groupId,
                        cont.coordinates.artifactId,
                        cont.coordinates.version,
                        cont.fileType
                    );
                    ctx.pipeToSelf(searchAssets(playMapper, client, formatted), (response, throwable) -> {
                        if (throwable != null) {
                            return new Failed(cont.coordinates, cont.existing, cont.replyTo, cont.completedReplyTo);
                        }
                        final var completedAssets = cont.existing.appendAll(response.items());
                        return response.continuationToken()
                            .<Gather>map(
                                token -> new ContinueRequest(
                                    cont.coordinates, cont.fileType, completedAssets, token, cont.replyTo,
                                    cont.completedReplyTo
                                ))
                            .orElseGet(() -> new Completed(cont.coordinates, completedAssets, cont.replyTo,
                                cont.completedReplyTo
                            ));
                    });
                    return Behaviors.same();
                })
                .onMessage(Completed.class, completed -> {
                    completed.replyTo.tell(
                        new ComponentsAvailable(completed.coordinates, completed.existing, completed.completedReplyTo));
                    return Behaviors.same();
                })
                .onMessage(Failed.class, failed -> {
                    failed.replyTo.tell(new FailedAssetRetrieval(failed.coordinates, 0, failed.completedReplyTo));
                    return Behaviors.same();
                })
                .build();
        });
    }

    private static CompletableFuture<AssetSearchResponse> searchAssets(
        final ObjectMapper playMapper,
        final HttpClient client,
        final String assetSearchUrl
    ) {
        return Try.of(() -> new URL(assetSearchUrl))
            .mapTry(URL::toURI)
            .map(url -> HttpRequest.newBuilder(url).GET())
            .toCompletableFuture()
            .thenCompose(req -> client.sendAsync(req.build(), HttpResponse.BodyHandlers.ofInputStream()))
            .thenApply(HttpResponse::body)
            .thenApply(stream -> Try.withResources(() -> stream)
                .of(is -> playMapper.readValue(is, AssetSearchResponse.class))
                .get());
    }

    private interface Registrar {
    }

    private static final record AttemptRegistration(MavenCoordinates coordinates,
                                                    List<Component.Asset> assets) implements Registrar {
    }

    private interface RegistrationResult extends Registrar {
    }

    private static final record AssetRegistrationCompleted(MavenCoordinates coordinates) implements RegistrationResult {
    }

    private static final record AssetRegistrationFailed(MavenCoordinates coordinates) implements RegistrationResult {
    }

    private static final record WrappedResult(RegistrationResult response) implements Registrar {
    }

    private static Behavior<Registrar> registerAssets(
        final VersionsService service,
        final AuthUtils auth
    ) {
        return Behaviors.setup(ctx -> Behaviors.receive(Registrar.class)
            .onMessage(AttemptRegistration.class, registration -> {
                final var artifacts = registration.assets.map(asset ->
                        Try.of(() -> URI.create(asset.downloadUrl()))
                            .map(downloadUri -> Optional.ofNullable(asset.mavenData())
                                .map(data -> new Artifact(
                                    Optional.ofNullable(data.classifier()),
                                    downloadUri,
                                    asset.checksum().md5(),
                                    asset.checksum().sha1(),
                                    asset.mavenData().extension()
                                ))
                            )
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .toJavaOptional()
                    )
                    .filter(Optional::isPresent)
                    .map(Optional::get);
                final var collection = new ArtifactCollection(artifacts, registration.coordinates);

                final var registrationFuture = service.registerArtifactCollection(
                        registration.coordinates.groupId,
                        registration.coordinates.artifactId
                    ).handleRequestHeader(request -> request.withHeader(
                        auth.internalHeaderKey(),
                        auth.internalHeaderSecret()
                    ))
                    .invoke(new VersionRegistration.Register.Collection(collection));

                ctx.pipeToSelf(registrationFuture, (response, throwable) -> {
                    if (throwable != null) {
                        ctx.getLog().error("Failed asset registration for ", throwable);
                        return new WrappedResult(new AssetRegistrationFailed(registration.coordinates));
                    }
                    return new WrappedResult(new AssetRegistrationCompleted(registration.coordinates));
                });
                return Behaviors.same();
            })
            .onMessage(WrappedResult.class, result -> {
                final var response = result.response;
                if (response instanceof AssetRegistrationCompleted a) {
                    ctx.getLog().debug("Successful registration of {}", a.coordinates);
                } else if (response instanceof AssetRegistrationFailed f) {
                    ctx.getLog().warn("Failed registration of {}", f.coordinates);
                }
                return Behaviors.same();
            })
            .build());
    }

}
