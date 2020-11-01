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
import org.spongepowered.downloads.artifact.api.query.GetTaggedArtifacts;
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
        if (event instanceof ArtifactProcessorEntity.Event.AssociateCommitSha associatedCommitSha) {
            return this.requestCommitsForArtifact(associatedCommitSha);
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
            final String tagVersion = component.assets()
                .filter(asset -> asset.path().endsWith(".pom"))
                .headOption()
                .map(client::resolvePomVersion)
                .flatMap(Try::get)
                .getOrElse(component::version);
            return this.artifacts.registerArtifacts()
                .invoke(new ArtifactRegistration.RegisterCollection(collection))
                .thenCompose(done -> this.webhookService
                    .getProcessingEntity(event.mavenCoordinates())
                    .ask(new ArtifactProcessorEntity.Command.AssociateMetadataWithCollection(collection, component, tagVersion))
                    .thenApply(notUsed -> Done.done())
                ).thenCompose(response -> this.artifacts
                    .registerTaggedVersion(event.mavenCoordinates(), tagVersion)
                    .invoke()
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

    private Done requestCommitsForArtifact(final ArtifactProcessorEntity.Event.AssociateCommitSha associatedCommitSha) {
        /*
        So, this is where we get a little tricky... We effectively need to "figure out" how many versions are available,
        and we can do this with a few calls to sonatype with some url generation, as an example:
        Given (artifactId: "spongeapi", groupId: "org.spongepowered", version: "8.0.0-SNAPSHOT")
        we can query the sonatype repo for the following:
        1) https://repo-new.spongepowered.org/repository/maven-snapshots/org/spongepowered/spongeapi/maven-metadata.xml,
        and with that information, we can verify that for the desired version, there's artifacts that'll exist, so next
        step is finding out how many "builds" of a particular version exists:
        2) https://repo-new.spongepowered.org/repository/maven-snapshots/org/spongepowered/spongeapi/8.0.0-SNAPSHOT/maven-metadata.xml
        With this, we can find out (at time of writing) there's 199 builds that exist, so we can compare the version build
        counts with what's available locally. Then, effectively, we can do something along the lines of
        3) https://repo-new.spongepowered.org/service/rest/v1/search/assets
        ?
        sort=version
        &direction=asc
        &maven.groupId=org.spongepowered
        &maven.artifactId=spongeapi
        &maven.baseVersion=8.0.0-SNAPSHOT
        &maven.extension=jar

        where we have the "maven.baseVersion" as the maven version vs the artifact version,
        we can specify the direction of objects to get returned in the version order something like this:
        https://gist.github.com/gabizou/c43d514de7f64cfda3e4659cd6b36ee2
        The unique aspect of the response is that we may have to specify by the "javadoc" classifier, or filter
        the artifacts by their path name, to gather the id's/build numbers to determine "ok, we know we've got these
        artifacts in our local, we may need to figure out which artifact is the 'previous' artifact,
        4) When we know which artifact is the previous artifact to this build, we can then query the commit service to
        gather the commits between the two SHAs, which we may end up asking the artifact service to query "hey, do we
        have the previous artifact?" and if not, we'll kick off an event to wait for the "previous builds committed"

         */


        this.artifacts.getTaggedArtifacts(associatedCommitSha.groupId(), associatedCommitSha.artifactId())
            .invoke(GetTaggedArtifacts.Request)
        return null;
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
