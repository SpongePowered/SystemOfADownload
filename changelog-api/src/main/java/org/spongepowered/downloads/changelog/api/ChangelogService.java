package org.spongepowered.downloads.changelog.api;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.changelog.api.query.ChangelogResponse;
import org.spongepowered.downloads.changelog.api.query.GenerateChangelogRequest;

import java.util.concurrent.CompletionStage;

public interface ChangelogService extends Service {

    ServiceCall<NotUsed, ChangelogResponse> getChangelog(String groupId, String artifactId, String version);

    @Override
    default Descriptor descriptor() {
        return Service.named("changelog")
            .withCalls(
              Service.restCall(Method.GET, "/api/:groupId/:artifactId/:version/changelog", this::getChangelog)
            );
    }

    ServiceCall<NotUsed, NotUsed> registerArtifact(Artifact artifact);

}
