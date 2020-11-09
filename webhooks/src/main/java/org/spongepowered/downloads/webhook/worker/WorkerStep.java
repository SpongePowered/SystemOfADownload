package org.spongepowered.downloads.webhook.worker;

import akka.Done;
import io.vavr.control.Try;
import org.apache.logging.log4j.Marker;
import org.spongepowered.downloads.webhook.ArtifactProcessorEntity;

sealed interface WorkerStep<E extends ArtifactProcessorEntity.Event>
    permits FetchPriorBuildStep, AssociateMavenMetadataStep, RegisterArtifactsStep {

    Try<Done> processEvent(SonatypeArtifactWorkerService service, E event);

    Marker marker();

}
