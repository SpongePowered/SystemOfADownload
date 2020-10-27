package org.spongepowered.downloads.artifact.api;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;

public interface ArtifactService extends Service {

    ServiceCall<NotUsed, GetArtifactsResponse> getArtifacts(String groupId);

    ServiceCall<ArtifactRegistration.RegisterArtifactRequest, ArtifactRegistration.Response> registerArtifact(String groupId);

    ServiceCall<ArtifactRegistration.RegisterCollection, ArtifactRegistration.Response> registerArtifacts();

    ServiceCall<GroupRegistration.RegisterGroupRequest, GroupRegistration.Response> registerGroup();

    ServiceCall<NotUsed, GroupResponse> getGroup(String s);

    @Override
    default Descriptor descriptor() {
        return Service.named("artifact")
            .withCalls(
                Service.restCall(Method.GET, "/api/:groupId/artifacts", this::getArtifacts),
                Service.restCall(Method.POST, "/api/admin/groups/create", this::registerGroup),
                Service.restCall(Method.POST, "/api/admin/:groupId/register", this::registerArtifact),
                Service.restCall(Method.GET, "/api/:groupId", this::getGroup)
            );
    }

}
