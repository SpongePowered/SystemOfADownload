package systemofadownload.artifacts.query;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import systemofadownload.artifacts.api.query.ArtifactRegistration;
import systemofadownload.artifacts.api.query.GetArtifactsResponse;

import java.util.concurrent.Flow;

@Controller("/groups/{groupID}/artifacts")
public class ArtifactQueryController {


    @Get("/")
    public Flow.Publisher<HttpResponse<GetArtifactsResponse>> getArtifacts(
        @PathVariable String groupID
    ) {
        return null;
    }
}
