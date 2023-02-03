package systemofadownload.groups.query;

import akka.actor.typed.ActorSystem;
import akka.stream.javadsl.AsPublisher;
import akka.stream.javadsl.JavaFlowSupport;
import akka.stream.javadsl.Source;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import jakarta.inject.Inject;
import systemofadownload.artifacts.api.query.GetArtifactsResponse;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Flow;

@Controller("/groups")
public class GroupsQueryController {

    @Inject
    private ActorSystem<?> system;

    @Get(uri = "/{groupID}")
    public Flow.Publisher<HttpResponse<GetArtifactsResponse>> g(@PathVariable String groupID) {
        return Source.from(Arrays.asList("", "b"))
            .via(akka.stream.javadsl.Flow.<String, HttpResponse<GetArtifactsResponse>>fromFunction(
                s -> HttpResponse.ok(new GetArtifactsResponse.ArtifactsAvailable(List.of(s)))
            ))
            .runWith(JavaFlowSupport.Sink.asPublisher(AsPublisher.WITH_FANOUT), this.system);
    }
}
