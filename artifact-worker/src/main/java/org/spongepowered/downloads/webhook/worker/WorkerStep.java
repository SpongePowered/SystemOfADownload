package org.spongepowered.downloads.webhook.worker;

import akka.Done;
import io.vavr.control.Try;
import org.apache.logging.log4j.Marker;
import org.spongepowered.downloads.webhook.ScrapedArtifactEvent;

interface WorkerStep<E extends ScrapedArtifactEvent> {

    Try<Done> processEvent(SonatypeArtifactWorkerService service, E event);

    Marker marker();

}
