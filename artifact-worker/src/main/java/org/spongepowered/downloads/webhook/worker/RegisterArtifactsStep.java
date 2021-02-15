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
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.transport.Method;
import com.lightbend.lagom.javadsl.api.transport.RequestHeader;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Try;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.utils.AuthUtils;
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
    private final Duration askTimeout = Duration.ofSeconds(5);

    public RegisterArtifactsStep() {
    }

    @Override
    public Try<Done> processEvent(
        final SonatypeArtifactWorkerService service,
        final ScrapedArtifactEvent.InitializeArtifactForProcessing event
    ) {
        return Try.of(
            () -> SonatypeArtifactWorkerService.authorizeInvoke(service.artifacts.getGroup(event.coordinates.groupId))
                .invoke()
                .thenComposeAsync(response -> {
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

    private CompletionStage<Done> processInitializationWithGroup(
        final SonatypeArtifactWorkerService service,
        final ScrapedArtifactEvent.InitializeArtifactForProcessing event,
        final GroupResponse.Available available
    ) {
        SonatypeArtifactWorkerService.LOGGER.log(Level.INFO, MARKER, "Starting to gather components from Nexus for event {}", event);
        final SonatypeClient client = SonatypeClient.configureClient().apply();
        final Try<Component> componentTry = client.resolveArtifact(event.componentId());
        final var newCollection = componentTry.map(component -> {
            final Component.Asset base = component.assets()
                // First, try "finding" the most appropriate jar
                .filter(asset -> filePattern.matcher(asset.path()).matches())
                .headOption()
                .getOrElse(() -> component.assets()
                    // Or else, just get the jar with the shortest path name length
                    .filter(asset -> asset.path().endsWith(".jar"))
                    .sortBy(Component.Asset::path)
                    .head()
                );
            SonatypeArtifactWorkerService.LOGGER.log(Level.INFO, MARKER, "Found base component {}", base);

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
                available.group.groupCoordinates + ":" + component.id() + ":" + component.version());
            final var updatedCollection = new ArtifactCollection(
                artifactByVariant,
                coordinates
            );
            SonatypeArtifactWorkerService.LOGGER.log(Level.INFO, MARKER, "Attempting registration {}", base);

            return service.artifacts.registerArtifacts(component.group())
                .handleRequestHeader(header -> header.withHeader(AuthUtils.INTERNAL_HEADER_KEY, AuthUtils.INTERNAL_HEADER_SECRET))
                .invoke(new ArtifactRegistration.RegisterArtifact(component.id(), component.name(), component.version()))
                .thenCompose(done -> SonatypeArtifactWorkerService.authorizeInvoke(service.artifacts.registerArtifactCollection(coordinates.groupId, coordinates.artifactId))
                    .invoke(new ArtifactRegistration.RegisterCollection(updatedCollection)))
                .thenCompose(done -> service.getProcessingEntity(event.mavenCoordinates())
                    .<NotUsed>ask(replyTo -> new ScrapedArtifactCommand.AssociateMetadataWithCollection(updatedCollection, component,
                        tagVersion,
                        replyTo
                    ), this.askTimeout)
                    .thenApply(notUsed -> Done.done())
                ).thenCompose(response -> SonatypeArtifactWorkerService.authorizeInvoke(service.artifacts
                    .registerTaggedVersion(event.mavenCoordinates(), tagVersion))
                    .invoke()
                    .thenApply(notUsed -> Done.done())
                );
        });
        return newCollection.getOrElseGet(
            throwable -> CompletableFuture.completedFuture(Done.done())
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
