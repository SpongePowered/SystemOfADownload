package systemofadownload.artifacts;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import systemofadownload.artifacts.api.query.ArtifactRegistration;
import systemofadownload.artifacts.api.query.GetArtifactsResponse;

import java.util.concurrent.Flow;

@Controller("/groups/{groupID}/artifacts")
public class ArtifactController {


    @Post("/")
    public HttpResponse<ArtifactRegistration.RegisterArtifact> createArtifact(
        @PathVariable String groupID,
        @Body ArtifactRegistration.RegisterArtifact registration
    ) {
        return null;
    }

    @Get("/{artifactID}")
    public Flow.Publisher<HttpResponse<String>> getArtifact(
        @PathVariable String groupID,
        @PathVariable String artifactID) {
        return null;
    }
}
