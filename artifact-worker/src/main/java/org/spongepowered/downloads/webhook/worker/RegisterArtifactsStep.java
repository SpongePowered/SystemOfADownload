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
package org.spongepowered.downloads.webhook.worker;

import akka.Done;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Try;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GetVersionsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.webhook.ScrapedArtifactEvent;
import org.spongepowered.downloads.webhook.sonatype.Component;
import org.spongepowered.downloads.webhook.sonatype.SonatypeClient;

import java.time.Duration;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class RegisterArtifactsStep
    implements WorkerStep<ScrapedArtifactEvent.InitializeArtifactForProcessing> {
    private static final Marker MARKER = MarkerManager.getMarker("ARTIFACT_REGISTRATION");
    private static final Pattern filePattern = Pattern.compile("(dev\\b|\\d+|shaded).jar$");
    private static final Logger LOGGER = LogManager.getLogger("ScrapedArtifactRegistrar");
    private final Duration askTimeout = Duration.ofSeconds(5);
    private final ObjectMapper objectMapper;

    public RegisterArtifactsStep(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Try<Done> processEvent(
        final SonatypeArtifactWorkerService service,
        final ScrapedArtifactEvent.InitializeArtifactForProcessing event
    ) {

        LOGGER.log(Level.INFO, "Receiving Event {}", event);
        return Try.of(
            () -> SonatypeArtifactWorkerService.authorizeInvoke(service.artifacts.getGroup(event.coordinates.groupId))
                .invoke()
                .thenComposeAsync(response -> {
                    LOGGER.log(Level.INFO, "Group Requested {}, received {}", event.coordinates.groupId, response);
                    if (response instanceof GroupResponse.Available) {
                        return this.processInitializationWithGroup(service, event, (GroupResponse.Available) response);
                    }
                    return CompletableFuture.completedFuture(Done.done());
                }).toCompletableFuture()
                .join()
        );
    }

    @Override
    public Marker marker() {
        return MARKER;
    }

    @Override
    public Logger logger() {
        return LOGGER;
    }

    private CompletionStage<Done> processInitializationWithGroup(
        final SonatypeArtifactWorkerService service,
        final ScrapedArtifactEvent.InitializeArtifactForProcessing event,
        final GroupResponse.Available available
    ) {
        LOGGER.log(Level.INFO, MARKER, "Starting to gather components from Nexus for event {}", event);
        final SonatypeClient client = SonatypeClient.configureClient(this.objectMapper).apply();
        final Try<Component> componentTry = client.resolveArtifact(event.componentId(), LOGGER);
        final var newCollection = componentTry.map(component -> {
            final Component.Asset base = component.assets()
                // First, try "finding" the most appropriate jar
                .filter(asset -> asset.path().endsWith(event.coordinates.artifactId + "-" + event.coordinates.version + ".jar"))
                .headOption()
                .getOrElse(() -> component.assets()
                    // Or else, just get the jar with the shortest path name length
                    .filter(asset -> asset.path().endsWith(".jar"))
                    .sortBy(Component.Asset::path)
                    .head()
                );
            LOGGER.log(Level.INFO, MARKER, "Found base component {}", base);

            final var artifacts = RegisterArtifactsStep.gatherArtifacts(available, component, base);
            final Map<String, Artifact> artifactByVariant = artifacts.toMap(
                artifact -> artifact.variant,
                Function.identity()
            );
            final String tagVersion = component.assets()
                .filter(asset -> asset.path().endsWith(".pom"))
                .headOption()
                .map(client::resolvePomVersion)
                .flatMap(Try::get)
                .getOrElse(component::version);
            final var coordinates = MavenCoordinates.parse(
                available.group.groupCoordinates + ":" + component.name() + ":" + component.version());
            final var updatedCollection = new ArtifactCollection(
                artifactByVariant,
                coordinates
            );
            SonatypeArtifactWorkerService.LOGGER.log(Level.INFO, MARKER, "Attempting registration {}", coordinates);

            LOGGER.log(Level.INFO, "Performing registration of collection {}", updatedCollection);
            return SonatypeArtifactWorkerService.authorizeInvoke(service.artifacts.registerArtifactCollection(coordinates.groupId, coordinates.artifactId))
                .invoke(new ArtifactRegistration.RegisterCollection(updatedCollection))
                .thenCompose(done -> service.artifacts.getArtifactVersions(coordinates.groupId, coordinates.artifactId)
                    .invoke()
                    .thenCompose(response -> {
                        if (!(response instanceof GetVersionsResponse.VersionsAvailable)) {
                            return CompletableFuture.completedFuture(Done.done());
                        }
                        LOGGER.info(MARKER, "Attempting to retrieve previous versions with existing versions {}", response);
                        return ensureVersionsUpdated(service, client, (GetVersionsResponse.VersionsAvailable) response, coordinates);
                    })
                );
        });
        return newCollection.getOrElseGet(
            throwable -> {
                LOGGER.log(Level.WARN, "Could not find artifact: {}, this artifact will be discarded from future processing", event.coordinates);
                return CompletableFuture.completedFuture(Done.done());
            }
        );
    }


    private static List<Artifact> gatherArtifacts(
        final GroupResponse.Available available, final Component component, final Component.Asset base
    ) {
        final var baseName = getBaseName(base.path());
        final var rawCoordinates = new StringJoiner(":")
            .add(available.group.groupCoordinates)
            .add(component.id())
            .add(component.version())
            .toString();
        final var coordinates = MavenCoordinates.parse(rawCoordinates);
        final var variants = component.assets().filter(asset -> asset.path().endsWith(".jar"))
            .filter(jar -> !jar.equals(base))
            .map(jar -> {
                final var variant = jar.path().replace(baseName, "").replace(".jar", "");

                return new Artifact(
                    variant, coordinates, jar.downloadUrl(),
                    jar.checksum().md5(), jar.checksum().sha1()
                );
            });
        return variants.prepend(
            new Artifact("base", coordinates, base.downloadUrl(),
                base.checksum().md5(), base.checksum().sha1()
            ));
    }

    private static CompletableFuture<Done> ensureVersionsUpdated(
        SonatypeArtifactWorkerService service, SonatypeClient client, GetVersionsResponse.VersionsAvailable response, MavenCoordinates coordinates
    ) {
        final boolean isSnapshot = coordinates.isSnapshot();
        final SonatypeClient sonatypeClient = client;
        final String groupId = coordinates.groupId;
        final String artifactId = coordinates.artifactId;
        final String mavenVersion = coordinates.asStandardCoordinates();
        final var request = new FetchPriorBuildStep.RecordRequest(groupId, artifactId, coordinates.toString(),
            mavenVersion, isSnapshot);
        if (isSnapshot) {
            final String[] split = coordinates.version.split("-");
            final String artifactBuildNumberString = split[split.length - 1];
            return sonatypeClient.getSnapshotBuildCount(groupId, artifactId, mavenVersion)
                .onFailure(FetchPriorBuildStep.logFailure(mavenVersion, groupId, artifactId))
                .toCompletableFuture()
                .thenApply(totalBuildCount -> {

                    final Try<CompletionStage<Either<Done, ArtifactCollection>>> completionStages = Try.of(
                        () -> Integer.parseInt(artifactBuildNumberString)
                    )
                        .flatMapTry(buildNumber -> FetchPriorBuildStep.getPriorBuildVersionOrRequestWork(
                            service,
                            sonatypeClient, request, buildNumber
                        ));
                    final Try<Either<Done, ArtifactCollection>> previousBuildOrRequested = completionStages
                        .map(CompletionStage::toCompletableFuture)
                        .map(CompletableFuture::join);

                    // Now, either associate the previous build
                    // with the incoming artifact, or just print "Done" since
                    // the prior build is likely needing to be requested for half processing
                    // of getting the previous (maybe missed?) artifact and see if we can
                    // associate the commit.
                    return Done.done();
                });

        }
        return CompletableFuture.completedFuture(Done.done());
    }
    /*
This assumes that the jars accepted as the "first" are either ending with a number (like date time)
or "dev" or "shaded" or "universal" jars. This also assumes that the jar being asked to get the
base name for will be the jar that has the shortest file name length.
 */
    private static String getBaseName(final String path) {
        return path.replace(".jar", "")
            .replace("dev", "")
            .replace("shaded", "")
            .replace("universal", "")
            ;
    }

    @Override
    public boolean equals(final Object obj) {
        return obj == this || obj != null && obj.getClass() == this.getClass();
    }

    @Override
    public int hashCode() {
        return 1;
    }

    @Override
    public String toString() {
        return "RegisterArtifactsStep[]";
    }

}
