package org.spongepowered.downloads.artifact.api;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistrationResponse;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistrationResponse;
import org.spongepowered.downloads.artifact.api.query.RegisterArtifactRequest;
import org.spongepowered.downloads.artifact.api.query.RegisterGroupRequest;

public interface ArtifactService extends Service {

    ServiceCall<NotUsed, GetArtifactsResponse> getArtifacts(String groupId);

    ServiceCall<RegisterArtifactRequest, ArtifactRegistrationResponse> registerArtifact(String groupId);

    ServiceCall<RegisterGroupRequest, GroupRegistrationResponse> registerGroup();

    @Override
    default Descriptor descriptor() {
        return Service.named("artifact")
            .withCalls(
                Service.restCall(Method.GET, "/api/:groupId/", this::getArtifacts),
                Service.restCall(Method.POST, "/api/admin/groups/create", this::registerGroup),
                Service.restCall(Method.POST, "/api/admin/:groupId/register", this::registerArtifact)
            );
    }
}
