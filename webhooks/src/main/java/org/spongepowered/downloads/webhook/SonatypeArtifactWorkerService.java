package org.spongepowered.downloads.webhook;

import akka.Done;
import akka.stream.javadsl.Flow;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Try;
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
            .atLeastOnce(Flow.<ArtifactProcessingEvent>create().map(this::processEvent));
        this.webhookService = webhookService;
    }

    private Done processEvent(final ArtifactProcessingEvent event) {
        if (event instanceof ArtifactProcessingEvent.InitializeArtifactForProcessing initialize) {
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
        return null;
    }

    private CompletionStage<Done> processInitializationWithGroup(
        final ArtifactProcessingEvent.InitializeArtifactForProcessing event,
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
            return new ArtifactCollection(
                artifactByVariant,
                available.group(),
                component.id(),
                component.version());
        });
        return newCollection.toEither()
            .fold(
                throwable -> CompletableFuture.completedFuture(Done.done()),
                collection ->
                    this.artifacts.registerArtifacts()
                        .invoke(new ArtifactRegistration.RegisterCollection(collection))
                        .thenApply(response -> Done.done())
                        .thenCompose(done -> this.webhookService
                            .getProcessingEntity(event.mavenCoordinates())
                            .ask(new ArtifactProcessingCommand.FetchJarAndPullMetadata(collection))
                            .thenApply(notUsed -> Done.done())
                        )
            );
    }

    private List<Artifact> gatherArtifacts(
        final GroupResponse.Available available, final Component component, final Component.Asset base
    ) {
        final var baseName = getBaseName(base.path());
        final var variants = component.assets().filter(asset -> asset.path().endsWith(".jar"))
            .filter(jar -> !jar.equals(base))
            .map(jar -> {
                final var variant = jar.path().replace(baseName, "").replace(".jar", "");
                return new Artifact(variant, available.group(), component.id(), component.version());
            });
        return variants.prepend(new Artifact("base", available.group(), component.id(), component.version()));
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
