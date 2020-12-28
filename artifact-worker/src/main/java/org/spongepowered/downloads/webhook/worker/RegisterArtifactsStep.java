package org.spongepowered.downloads.webhook.worker;

import akka.Done;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Try;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.webhook.ScrapedArtifactEvent;
import org.spongepowered.downloads.webhook.sonatype.Component;
import org.spongepowered.downloads.webhook.sonatype.SonatypeClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.regex.Pattern;

public final record RegisterArtifactsStep()
    implements WorkerStep<ScrapedArtifactEvent.InitializeArtifactForProcessing> {
    private static final Marker MARKER = MarkerManager.getMarker("ARTIFACT_REGISTRATION");
    private static final Pattern filePattern = Pattern.compile("(dev\\b|\\d+|shaded).jar$");

    @Override
    public Try<Done> processEvent(
        final SonatypeArtifactWorkerService service,
        final ScrapedArtifactEvent.InitializeArtifactForProcessing event
    ) {
        return Try.of(
            () -> service.artifacts.getGroup(event.mavenCoordinates().split(":")[0])
                .invoke()
                .thenComposeAsync(response -> {
                    if (response instanceof GroupResponse.Available available) {
                        return this.processInitializationWithGroup(service, event, available);
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
            final var artifacts = RegisterArtifactsStep.gatherArtifacts(available, component, base);
            final Map<String, Artifact> artifactByVariant = artifacts.toMap(
                Artifact::variant,
                Function.identity()
            );
            final String tagVersion = component.assets()
                .filter(asset -> asset.path().endsWith(".pom"))
                .headOption()
                .map(client::resolvePomVersion)
                .flatMap(Try::get)
                .getOrElse(component::version);
            final var updatedCollection = new ArtifactCollection(
                artifactByVariant,
                available.group(),
                component.id(),
                component.version(),
                tagVersion
            );
            return service.artifacts.registerArtifacts()
                .invoke(new ArtifactRegistration.RegisterCollection(updatedCollection))
                .thenCompose(done -> service.getProcessingEntity(event.mavenCoordinates())
                    .ask(new ScrapedArtifactEntity.Command.AssociateMetadataWithCollection(updatedCollection, component,
                        tagVersion
                    ))
                    .thenApply(notUsed -> Done.done())
                ).thenCompose(response -> service.artifacts
                    .registerTaggedVersion(event.mavenCoordinates(), tagVersion)
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
        final var variants = component.assets().filter(asset -> asset.path().endsWith(".jar"))
            .filter(jar -> !jar.equals(base))
            .map(jar -> {
                final var variant = jar.path().replace(baseName, "").replace(".jar", "");
                return new Artifact(
                    variant, available.group(), component.id(), component.version(), jar.downloadUrl(),
                    jar.checksum().md5(), jar.checksum().sha1()
                );
            });
        return variants.prepend(
            new Artifact("base", available.group(), component.id(), component.version(), base.downloadUrl(),
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
}
