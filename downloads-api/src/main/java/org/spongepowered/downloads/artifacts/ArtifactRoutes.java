package org.spongepowered.downloads.artifacts;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.concat;
import static akka.http.javadsl.server.Directives.get;
import static akka.http.javadsl.server.Directives.onSuccess;
import static akka.http.javadsl.server.Directives.path;
import static akka.http.javadsl.server.Directives.pathPrefix;
import static akka.http.javadsl.server.Directives.rejectEmptyResponse;

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
import org.spongepowered.downloads.artifacts.transport.GetArtifactsResponse;
import org.spongepowered.downloads.artifacts.transport.GroupResponse;
import org.spongepowered.downloads.artifacts.transport.GroupsResponse;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public final class ArtifactRoutes {

    public static final Logger logger = LoggerFactory.getLogger(ArtifactRoutes.class);

    private final ActorRef<ArtifactQueries.Command> artifactQueries;
    private final Duration askTimeout;
    private final Scheduler scheduler;

    public ArtifactRoutes(ActorSystem<?> system, ActorRef<ArtifactQueries.Command> artifactQueries) {
        this.artifactQueries = artifactQueries;
        scheduler = system.scheduler();
        askTimeout = system.settings().config().getDuration("my-app.routes.ask-timeout");
    }

    private CompletionStage<GroupsResponse> getGroups() {
        return AskPattern.ask(artifactQueries, ArtifactQueries.Command.GetGroups::new, askTimeout, scheduler);
    }

    private CompletionStage<GroupResponse> getGroup(String name) {
        return AskPattern.ask(
            artifactQueries, ref -> new ArtifactQueries.Command.GetGroup(name, ref), askTimeout, scheduler);
    }

    private CompletionStage<GetArtifactsResponse> getArtifacts(String name) {
        return AskPattern.ask(
            artifactQueries, ref -> new ArtifactQueries.Command.GetArtifacts(name, ref), askTimeout, scheduler);
    }


    /**
     * This method creates one route (of possibly many more that will be part of your Web App)
     */
    public Route artifactRoutes() {
        // v1/groups
        return pathPrefix("groups", () ->
            concat(
                get(() -> onSuccess(getGroups(), groups ->
                    complete(StatusCodes.OK, groups, Jackson.marshaller())
                )),
                // v1/groups/:groupId/
                path(PathMatchers.segment(), (String groupId) ->
                    concat(
                        get(() -> rejectEmptyResponse(() ->
                            onSuccess(getGroup(groupId), performed -> {
                                final var group = performed.group();
                                logger.info("Groups get: {}", group);
                                if (group.isEmpty()) {
                                    return complete(StatusCodes.BAD_REQUEST, "group not found");
                                }
                                return complete(StatusCodes.OK, group, Jackson.marshaller());
                            }))),
                        // v1/groups/:groupId/artifacts
                        pathPrefix("artifacts", () -> get(() -> rejectEmptyResponse(() ->
                            onSuccess(getArtifacts(groupId), performed -> {
                                logger.info("Get result: {}", performed.artifactIds());
                                return complete(StatusCodes.OK, performed, Jackson.marshaller());
                            })))
                    ))
            )
        ));
    }
}
