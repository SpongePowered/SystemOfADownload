package org.spongepowered.downloads.webhook;


import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.routing.ClusterRouterGroup;
import akka.cluster.routing.ClusterRouterGroupSettings;
import akka.routing.ConsistentHashingGroup;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistrationResponse;
import org.spongepowered.downloads.artifact.api.query.RegisterArtifactRequest;
import org.spongepowered.downloads.changelog.api.ChangelogService;
import org.spongepowered.downloads.changelog.api.query.GenerateChangelogRequest;
import org.spongepowered.downloads.webhook.worker.Worker;

import java.util.concurrent.CompletableFuture;

public class SonatypeWebhookService implements Service {

    private final ActorRef workerRouter;
    private final ArtifactService artifacts;
    private final ChangelogService changelog;

    @Inject
    public SonatypeWebhookService(
        final ActorSystem system, final ArtifactService artifacts, final ChangelogService changelog
    ) {
        this.artifacts = artifacts;
        this.changelog = changelog;
        if (Cluster.get(system).getSelfRoles().contains("worker-node")) {
            // start a worker actor on each node that has the "worker-node" role
            system.actorOf(Worker.props(), "worker");
        }

        // start a consistent hashing group router,
        // which will delegate jobs to the workers. It is grouping
        // the jobs by their task, i.e. jobs with same task will be
        // delegated to same worker node
        List<String> paths = Arrays.asList("/user/worker");
        ConsistentHashingGroup groupConf =
            new ConsistentHashingGroup(paths)
                .withHashMapper(
                    msg -> {
                        if (msg instanceof Job) {
                            return ((Job) msg).getTask();
                        } else {
                            return null;
                        }
                    });
        Set<String> useRoles = new TreeSet<>();
        useRoles.add("worker-node");

        Props routerProps =
            new ClusterRouterGroup(
                groupConf, new ClusterRouterGroupSettings(1000, paths, true, useRoles))
                .props();
        this.workerRouter = system.actorOf(routerProps, "workerRouter");
    }

    ServiceCall<SonatypeData, NotUsed> processSonatypeData() {
        return (webhook) -> {
            if ("CREATED".equals(webhook.action)) {
                final SonatypeComponent component = webhook.component;
                return this.artifacts.registerArtifact(component.group)
                    .invoke(new RegisterArtifactRequest(component.id, component.version))
                    .thenComposeAsync(response -> {
                        if (response instanceof ArtifactRegistrationResponse.RegisteredArtifact registered) {
                            final var request = new GenerateChangelogRequest(registered.artifact(), component.componentId);
                            return this.changelog.generateChangelog().invoke(request)
                                .thenApply(changelogResponse -> NotUsed.notUsed());
                        }
                        return CompletableFuture.completedStage(NotUsed.notUsed());
                    })
                    .thenApply(response -> NotUsed.notUsed());
            }
            return CompletableFuture.completedStage(NotUsed.notUsed());
        };
    }

    @JsonDeserialize
    static record SonatypeData(String timestamp, String nodeId, String initiator, String repositoryName, String action, SonatypeComponent component) {}
    @JsonDeserialize
    static record SonatypeComponent(String id, String componentId, String format, String name, String group, String version) {}
    @Override
    public Descriptor descriptor() {
        return Service.named("webhooks")
            .withCalls(
                Service.restCall(Method.POST, "/api/webhook", this::processSonatypeData)
            );
    }
}
