package systemofadownload.groups;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.SpawnProtocol;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.stream.javadsl.AsPublisher;
import akka.stream.javadsl.JavaFlowSupport;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorFlow;
import akka.stream.typed.javadsl.ActorSink;
import io.micronaut.context.annotation.Bean;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import jakarta.inject.Inject;
import systemofadownload.artifacts.api.query.GetArtifactsResponse;
import systemofadownload.artifacts.api.query.GroupRegistration;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Flow;

@Controller("/groups")
public class GroupController {

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
