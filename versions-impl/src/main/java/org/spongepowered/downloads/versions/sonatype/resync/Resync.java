package org.spongepowered.downloads.versions.sonatype.resync;

import akka.actor.typed.ActorRef;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;

public final class Resync {
    public final ActorRef<List<MavenCoordinates>> replyTo;
    public final ArtifactCoordinates coordinates;

    public Resync(ArtifactCoordinates coordinates, ActorRef<List<MavenCoordinates>> replyTo) {
        this.coordinates = coordinates;
        this.replyTo = replyTo;
    }
}