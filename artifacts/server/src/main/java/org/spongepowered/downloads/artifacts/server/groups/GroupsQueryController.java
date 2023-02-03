package org.spongepowered.downloads.artifacts.server.groups;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.SpawnProtocol;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import jakarta.inject.Inject;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Controller("/groups")
public class GroupsQueryController {

    private final ActorSystem<SpawnProtocol.Command> system;
    private final ClusterSharding sharding;

    @Inject
    public GroupsQueryController(
        final ActorSystem<SpawnProtocol.Command> system,
        ClusterSharding sharding
    ) {
        this.system = system;
        this.sharding = sharding;
        this.sharding.init(
            Entity.of(
                GroupEntity.ENTITY_TYPE_KEY,
                GroupEntity::create
            )
        );

    }

    @Post("/")
    public CompletableFuture<HttpResponse<GroupRegistration.Response>> registerGroup(
        @Body GroupRegistration.RegisterGroupRequest req
    ) {
        final var ref = this.sharding.entityRefFor(
            GroupEntity.ENTITY_TYPE_KEY,
            req.groupCoordinates()
        );
        final var resp = ref.<GroupRegistration.Response>ask
            (
                replyTo -> new GroupCommand.RegisterGroup(
                    req.groupCoordinates(),
                    req.name(),
                    req.website(),
                    replyTo
                ),
                Duration.ofSeconds(10)
            );
        return resp
            .<HttpResponse<GroupRegistration.Response>>thenApply(HttpResponse::created)
            .toCompletableFuture();
    }
}
