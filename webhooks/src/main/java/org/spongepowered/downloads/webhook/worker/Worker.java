package org.spongepowered.downloads.webhook.worker;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class Worker extends AbstractActor {

    public static Props props() {
        return Props.create(Worker.class);
    }

    private final LoggingAdapter log = Logging.getLogger(this.context().system(), this);

    @Override
    public Receive createReceive() {
        return this.receiveBuilder()
            .match(Job.class, this::fetchJarMetadata)
            .build();
    }

    private void fetchJarMetadata(final Job job) {
        if (job instanceof Job.FetchJarMetadataJob fetcher) {
            fetcher
        }
        sender().tell();
    }
}
