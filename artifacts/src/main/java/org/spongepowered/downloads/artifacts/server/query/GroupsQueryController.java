package org.spongepowered.downloads.artifacts.server.query;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.SpawnProtocol;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import jakarta.inject.Inject;

@Controller("/groups")
public class GroupsQueryController {


    @Inject
    private ActorSystem<SpawnProtocol.Command> system;
    @Inject
    private ClusterSharding sharding;

    @Post("/")
    public HttpResponse<GroupRegistration.Response> registerGroup(
        @Body GroupRegistration.RegisterGroupRequest req
    ) {
        return null;
    }
}
