package org.spongepowered.downloads.git.api;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.vavr.collection.List;
import org.taymyr.lagom.javadsl.openapi.OpenAPIService;
import org.taymyr.lagom.javadsl.openapi.OpenAPIUtils;

import java.util.Optional;

@OpenAPIDefinition(
    info = @Info(
        title = "CommitService",
        description = "Manages understanding of a commit in a Repository, by itself is to know about git commits",
        contact = @Contact(
            name = "SpongePowered",
            url = "https://spongepowered.org/",
            email = "dev@spongepowered.org"
        ),
        license = @License(
            name = "MIT",
            url = "https://opensource.org/licenses/MIT"
        )
    )
)
public interface CommitService extends OpenAPIService {

    @Operation(
        method = "POST",
        summary = "Given a commit diff body, get the list of commits between the fromSha and toSha",
        requestBody = @RequestBody(
            description = "The commit list requrest object",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CommitDiff.class)
            )
        )
    )
    ServiceCall<CommitDiff, List<Commit>> getGitDiff(String repo);

    ServiceCall<RepositoryRegistration, Repository> registerRepository();

    ServiceCall<NotUsed, Optional<Commit>> getCommit(String repo, String commit);


    @Override
    default Descriptor descriptor() {
        return OpenAPIUtils.withOpenAPI(Service.named("commit")
            .withCalls(
                Service.restCall(Method.POST, "/api/repository/:repo/commit/diff", this::getGitDiff),
                Service.restCall(Method.GET, "/api/repository/:repo/:commit", this::getCommit),
                Service.restCall(Method.POST, "/api/repository/register", this::registerRepository)
            )
            .withAutoAcl(true));
    }
}
