package org.spongepowered.downloads.artifact.api;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GetTaggedArtifacts;
import org.spongepowered.downloads.artifact.api.query.GetVersionsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;

import java.util.concurrent.CompletionStage;

public interface ArtifactService extends Service {

    ServiceCall<NotUsed, GetArtifactsResponse> getArtifacts(String groupId);

    ServiceCall<NotUsed, GetVersionsResponse> getArtifactVersions(String groupId, String artifactId);

    ServiceCall<GetTaggedArtifacts.Request, GetTaggedArtifacts.Response> getTaggedArtifacts(String groupId, String artifactId);

    ServiceCall<ArtifactRegistration.RegisterCollection, ArtifactRegistration.Response> registerArtifacts();

    ServiceCall<GroupRegistration.RegisterGroupRequest, GroupRegistration.Response> registerGroup();

    ServiceCall<NotUsed, GroupResponse> getGroup(String s);
    ServiceCall<NotUsed, NotUsed> registerTaggedVersion(String mavenCoordinates, String tagVersion);

    @Override
    default Descriptor descriptor() {
        return Service.named("artifact")
            .withCalls(
                Service.restCall(Method.GET, "/api/:groupId", this::getGroup),
                Service.restCall(Method.GET, "/api/:groupId/artifacts", this::getArtifacts),
                Service.restCall(Method.GET, "/api/:groupId/artifact/:artifactId", this::getArtifactVersions),
                Service.restCall(Method.POST, "/api/admin/groups/create", this::registerGroup)
            );
    }

}
