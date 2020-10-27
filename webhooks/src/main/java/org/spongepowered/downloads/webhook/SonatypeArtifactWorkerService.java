package org.spongepowered.downloads.webhook;

import akka.Done;
import akka.stream.javadsl.Flow;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Try;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.changelog.api.ChangelogService;
import org.spongepowered.downloads.webhook.sonatype.Component;
import org.spongepowered.downloads.webhook.sonatype.SonatypeClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.regex.Pattern;

public class SonatypeArtifactWorkerService implements Service {

    private static final Logger LOGGER = LogManager.getLogger(SonatypeArtifactWorkerService.class);

    private static final Pattern filePattern = Pattern.compile("(dev\\b|\\d+|shaded).jar$");

    private final ArtifactService artifacts;
    private final ChangelogService changelog;
    private final SonatypeWebhookService webhookService;

    @Inject
    public SonatypeArtifactWorkerService(
        final ArtifactService artifacts, final ChangelogService changelog,
        final SonatypeWebhookService webhookService
    ) {
        this.artifacts = artifacts;
        this.changelog = changelog;
        webhookService.topic().subscribe()
            .atLeastOnce(Flow.<ArtifactProcessorEntity.Event>create().map(this::processEvent));
        this.webhookService = webhookService;
    }

    private Done processEvent(final ArtifactProcessorEntity.Event event) {
        if (event instanceof ArtifactProcessorEntity.Event.InitializeArtifactForProcessing initialize) {
            return this.artifacts.getGroup(initialize.mavenCoordinates().split(":")[0])
                .invoke()
                .thenComposeAsync(response -> {
                    if (response instanceof GroupResponse.Available available) {
                        return this.processInitializationWithGroup(initialize, available);
                    }
                    return CompletableFuture.completedFuture(Done.done());
                }).toCompletableFuture()
                .join();
        }
        if (event instanceof ArtifactProcessorEntity.Event.AssociatedMavenMetadata association) {
            return this.downloadFileToGrabMetadata(association);
        }
        return null;
    }

    private CompletionStage<Done> processInitializationWithGroup(
        final ArtifactProcessorEntity.Event.InitializeArtifactForProcessing event,
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
            final var artifacts = this.gatherArtifacts(available, component, base);
            final Map<String, Artifact> artifactByVariant = artifacts.toMap(
                Artifact::variant,
                Function.identity()
            );
            final var collection = new ArtifactCollection(
                artifactByVariant,
                available.group(),
                component.id(),
                component.version()
            );
            return this.artifacts.registerArtifacts()
                .invoke(new ArtifactRegistration.RegisterCollection(collection))
                .thenCompose(done -> this.webhookService
                    .getProcessingEntity(event.mavenCoordinates())
                    .ask(new ArtifactProcessorEntity.Command.AssociateMetadataWithCollection(collection, component))
                    .thenApply(notUsed -> Done.done())
                );
        });
        return newCollection.getOrElseGet(
            throwable -> CompletableFuture.completedFuture(Done.done())
        );
    }

    private Done downloadFileToGrabMetadata(final ArtifactProcessorEntity.Event.AssociatedMavenMetadata event) {
        final SonatypeClient client = SonatypeClient.configureClient().apply();
        final Artifact base = event.collection().getArtifactComponents().get("base")
            .getOrElse(() -> event.collection().getArtifactComponents().head()._2);
        return client.generateArtifactFrom(base)
            .map(sha -> this.webhookService.getProcessingEntity(event.mavenCoordinates())
                .ask(new ArtifactProcessorEntity.Command.AssociateCommitShaWithArtifact(event.collection(), sha))
            )
            .map(notUsed -> notUsed.thenApply(notUsed1 -> Done.done()))
            .toEither()
            .mapLeft(throwable -> {
                LOGGER.log(Level.WARN, throwable);
                return CompletableFuture.completedFuture(Done.done());
            })
            .fold(Function.identity(), Function.identity())
            .toCompletableFuture()
            .join();
    }

    private List<Artifact> gatherArtifacts(
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

    @Override
    public Descriptor descriptor() {
        return Service.named("artifact-worker");
    }
}
