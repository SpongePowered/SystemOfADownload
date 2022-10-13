package org.spongepowered.downloads.versions;

import akka.actor.typed.ActorRef;
import org.spongepowered.downloads.api.ArtifactCoordinates;
import org.spongepowered.downloads.api.MavenCoordinates;
import org.spongepowered.downloads.versions.transport.QueryLatest;
import org.spongepowered.downloads.versions.transport.QueryVersions;

class VersionQueries {

    sealed interface Command {
        record GetVersion(MavenCoordinates coordinates, ActorRef<QueryLatest> replyTo) implements Command {}
        record GetVersions(ArtifactCoordinates coordinates, ActorRef<QueryVersions.VersionInfo> replyTo) implements Command {}
    }
}
