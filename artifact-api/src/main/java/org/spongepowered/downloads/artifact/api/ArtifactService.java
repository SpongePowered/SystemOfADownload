package org.spongepowered.downloads.artifact.api;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import io.vavr.collection.List;

public interface ArtifactService extends Service {

    ServiceCall<NotUsed, List<Artifact>> getArtifacts(String groupId);

    @Override
    default Descriptor descriptor() {
        return Service.named("artifact")
            .withCalls(
                Service.restCall(Method.GET, "/api/:groupId/", this::getArtifacts)
            );
    }
}
