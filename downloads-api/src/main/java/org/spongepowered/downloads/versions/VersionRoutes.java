package org.spongepowered.downloads.versions;


import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.concat;
import static akka.http.javadsl.server.Directives.get;
import static akka.http.javadsl.server.Directives.onSuccess;
import static akka.http.javadsl.server.Directives.pathPrefix;
import static akka.http.javadsl.server.PathMatchers.segment;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.downloads.api.ArtifactCoordinates;
import org.spongepowered.downloads.versions.transport.QueryVersions;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public final class VersionRoutes {

    public static final Logger logger = LoggerFactory.getLogger(
        org.spongepowered.downloads.artifacts.ArtifactRoutes.class);

    private final ActorRef<VersionQueries.Command> artifactQueries;
    private final Duration askTimeout;
    private final Scheduler scheduler;

    public VersionRoutes(ActorSystem<?> system, ActorRef<VersionQueries.Command> artifactQueries) {
        this.artifactQueries = artifactQueries;
        scheduler = system.scheduler();
        askTimeout = system.settings().config().getDuration("my-app.routes.ask-timeout");
    }

    CompletionStage<QueryVersions.VersionInfo> getVersions(ArtifactCoordinates coordinates) {
        return AskPattern.ask(this.artifactQueries, ref -> new VersionQueries.Command.GetVersions(
            coordinates, ref
        ), this.askTimeout, this.scheduler);
    }


    /**
     * This method creates one route (of possibly many more that will be part of your Web App)
     */
    public Route artifactRoutes() {
        // v1/groups
        return pathPrefix(
            segment("groups").slash(segment()).slash(segment("artifacts").slash(segment())),
            (groupId, artifactId) ->
                concat(
                    get(() -> onSuccess(this.getVersions(new ArtifactCoordinates(groupId, artifactId)), (resp) -> {
                        if (resp.size() <= 0) {
                            return complete(StatusCodes.NOT_FOUND);
                        }
                        return complete(StatusCodes.OK, resp, Jackson.marshaller());
                    })),
                    path(PathMatchers.segment("versions").slash(), version -> get(() ->
                        onSuccess(this.getVersions(), resp -> {
                            return complete(StatusCodes.OK, resp, Jackson.marshaller());
                        })))

                    )
        );
    }
}
