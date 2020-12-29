package org.spongepowered.downloads.webhook.worker;

import akka.Done;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.query.GetVersionsResponse;
import org.spongepowered.downloads.webhook.ScrapedArtifactEvent;
import org.spongepowered.downloads.webhook.sonatype.SonatypeClient;

import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

public final class FetchPriorBuildStep implements WorkerStep<ScrapedArtifactEvent.AssociateCommitSha> {

    private static final Marker MARKER = MarkerManager.getMarker("FETCH_PRIOR_BUILD");

    public FetchPriorBuildStep() {
    }

    final static class RecordRequest {
        private final String groupId;
        private final String artifactId;
        private final String coordinates;
        private final String mavenVersion;
        private final boolean isSnapshot;

        RecordRequest(
            String groupId,
            String artifactId,
            String coordinates,
            String mavenVersion,
            boolean isSnapshot
        ) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.coordinates = coordinates;
            this.mavenVersion = mavenVersion;
            this.isSnapshot = isSnapshot;
        }

        public String groupId() {
            return this.groupId;
        }

        public String artifactId() {
            return this.artifactId;
        }

        public String coordinates() {
            return this.coordinates;
        }

        public String mavenVersion() {
            return this.mavenVersion;
        }

        public boolean isSnapshot() {
            return this.isSnapshot;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (RecordRequest) obj;
            return Objects.equals(this.groupId, that.groupId) &&
                Objects.equals(this.artifactId, that.artifactId) &&
                Objects.equals(this.coordinates, that.coordinates) &&
                Objects.equals(this.mavenVersion, that.mavenVersion) &&
                this.isSnapshot == that.isSnapshot;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.groupId, this.artifactId, this.coordinates, this.mavenVersion, this.isSnapshot);
        }

        @Override
        public String toString() {
            return "RecordRequest[" +
                "groupId=" + this.groupId + ", " +
                "artifactId=" + this.artifactId + ", " +
                "coordinates=" + this.coordinates + ", " +
                "mavenVersion=" + this.mavenVersion + ", " +
                "isSnapshot=" + this.isSnapshot + ']';
        }

    }

    @Override
    public Try<Done> processEvent(
        final SonatypeArtifactWorkerService service,
        final ScrapedArtifactEvent.AssociateCommitSha event
    ) {
        // Effectively, we can do a few things here:
        /*
        - Basically we need to update the artifact's tagged version list, since sonatype will tell us at this
        point what versions are available, and if it's a snapshot, we can grab the number of builds for that
        snapshot.
        - After which, we can request locally what we've got, compare and contrast, if we're up to date, we
        can effectively query for the previous artifact. If we're not up to date, submit a new command/request
        to start an artifact process for the previous version and then submit the "resume" event.
         */
        final String mavenVersion = event.collection().getMavenVersion();
        final boolean isSnapshot = mavenVersion.endsWith("-SNAPSHOT");
        final SonatypeClient sonatypeClient = SonatypeClient.configureClient().get();
        final String groupId = event.groupId();
        final String artifactId = event.artifactId();
        final String coordinates = event.mavenCoordinates();
        final var request = new RecordRequest(groupId, artifactId, coordinates, mavenVersion, isSnapshot);
        if (isSnapshot) {
            final String[] split = event.version().split("-");
            final String artifactBuildNumberString = split[split.length - 1];
            return sonatypeClient.getSnapshotBuildCount(groupId, artifactId, mavenVersion)
                .onFailure(this.logFailure(mavenVersion, groupId, artifactId))
                .flatMapTry(totalBuildCount -> {

                    final Try<CompletionStage<Either<Done, ArtifactCollection>>> completionStages = Try.of(
                        () -> Integer.parseInt(artifactBuildNumberString)
                    )
                        .flatMapTry(buildNumber -> FetchPriorBuildStep.getPriorBuildVersionOrRequestWork(service,
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
                    return Try.success(Done.done());
                });

        }

        return null;
    }

    private static Try<? extends CompletionStage<Either<Done, ArtifactCollection>>> getPriorBuildVersionOrRequestWork(
        final SonatypeArtifactWorkerService service, final SonatypeClient sonatypeClient, final RecordRequest request,
        final int buildNum
    ) {
        return sonatypeClient
            .getSnapshotVersions(request.groupId, request.artifactId, request.mavenVersion, buildNum)
            .map(buildVersionByNumber -> buildVersionByNumber.get(buildNum - 1)
                .map(buildVersion -> FetchPriorBuildStep.getPriorBuildVersion(service, request,
                    buildVersion
                ))
                .getOrElse(CompletableFuture.completedFuture(Either.left(Done.done()))));
    }

    private static CompletionStage<Either<Done, ArtifactCollection>> getPriorBuildVersion(
        final SonatypeArtifactWorkerService service,
        final RecordRequest request,
        final String priorBuildVersion
    ) {
        return service.artifacts.getArtifactVersions(request.groupId, request.artifactId)
            .invoke()
            .thenCompose(
                FetchPriorBuildStep.getPreviousBuildVersionOrSubmitRequest(service, request, priorBuildVersion)
            );
    }

    private static Function<GetVersionsResponse, CompletionStage<Either<Done, ArtifactCollection>>> getPreviousBuildVersionOrSubmitRequest(
        final SonatypeArtifactWorkerService service,
        final RecordRequest request,
        final String priorBuildVersion
    ) {
        return response -> {
            if (response instanceof GetVersionsResponse.VersionsAvailable) {
                final var va = (GetVersionsResponse.VersionsAvailable) response;
                final Option<ArtifactCollection> artifactCollections = va.artifacts()
                    .get(priorBuildVersion);
                final Either<Done, ArtifactCollection> collectionOrDone = artifactCollections.toEither(
                    () -> {
                        final String previousBuildCoordinates = new StringJoiner(":")
                            .add(request.groupId)
                            .add(request.artifactId)
                            .add(priorBuildVersion)
                            .toString();
                        return service.getProcessingEntity(previousBuildCoordinates)
                            .ask(new ScrapedArtifactEntity.Command.RequestArtifactForProcessing(request.groupId,
                                request.artifactId, priorBuildVersion
                            ))
                            .thenApply(notUsed -> Done.done())
                            .toCompletableFuture()
                            .join();
                    });
                return CompletableFuture.completedFuture(collectionOrDone);
            }
            SonatypeArtifactWorkerService.LOGGER
                .warn(MARKER, String.format("Got invalid response %s", response));
            return CompletableFuture.completedFuture(Either.<Done, ArtifactCollection>left(Done.done()));
        };
    }

    private Consumer<Throwable> logFailure(final String mavenVersion, final String groupId, final String artifactId) {
        return throwable -> SonatypeArtifactWorkerService.LOGGER.log(
            Level.WARN,
            FetchPriorBuildStep.MARKER,
            String.format(
                "Failed to parse prior build information for artifact %s:%s:%s with error %s",
                groupId,
                artifactId,
                mavenVersion,
                throwable
            )
        );
    }

    @Override
    public Marker marker() {
        return MARKER;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || obj != null && obj.getClass() == this.getClass();
    }

    @Override
    public int hashCode() {
        return 1;
    }

    @Override
    public String toString() {
        return "FetchPriorBuildStep[]";
    }

}
