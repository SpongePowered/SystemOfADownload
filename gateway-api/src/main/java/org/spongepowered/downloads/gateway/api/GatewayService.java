package org.spongepowered.downloads.gateway.api;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.artifact.api.query.GroupsResponse;
import org.spongepowered.downloads.artifacts.query.api.GetArtifactDetailsResponse;
import org.spongepowered.downloads.auth.api.AuthenticationRequest;
import org.spongepowered.downloads.versions.api.models.TagRegistration;
import org.spongepowered.downloads.versions.api.models.TagVersion;
import org.spongepowered.downloads.versions.api.models.VersionRegistration;
import org.spongepowered.downloads.versions.query.api.models.QueryVersions;

import java.util.Optional;

public interface GatewayService extends Service {

    ServiceCall<NotUsed, GetArtifactsResponse> getArtifacts(String groupId);

    ServiceCall<ArtifactRegistration.RegisterArtifact, ArtifactRegistration.Response> registerArtifacts(
        String groupId
    );

    ServiceCall<GroupRegistration.RegisterGroupRequest, GroupRegistration.Response> registerGroup();

    ServiceCall<NotUsed, GroupResponse> getGroup(String groupId);

    ServiceCall<NotUsed, GroupsResponse> getGroups();
    ServiceCall<NotUsed, GetArtifactDetailsResponse> getArtifactDetails(String groupId, String artifactId);
    ServiceCall<NotUsed, AuthenticationRequest.Response> login();
    ServiceCall<NotUsed, NotUsed> logout();

    ServiceCall<VersionRegistration.Register, VersionRegistration.Response> registerArtifactCollection(
        String groupId, String artifactId
    );

    ServiceCall<TagRegistration.Register, TagRegistration.Response> registerArtifactTag(String groupId, String artifactId);
    ServiceCall<TagRegistration.Register, TagRegistration.Response> updateArtifactTag(String groupId, String artifactId);

    ServiceCall<TagVersion.Request, TagVersion.Response> tagVersion(String groupId, String artifactId);

    ServiceCall<NotUsed, QueryVersions.VersionInfo> artifactVersions(
        String groupId, String artifactId, Optional<String> tags, Optional<Integer> limit,
        Optional<Integer> offset, Optional<Boolean> recommended
    );

    ServiceCall<NotUsed, QueryVersions.VersionDetails> latestArtifact(
        String groupId, String artifactId, Optional<String> tags, Optional<Boolean> recommended
    );

    ServiceCall<NotUsed, QueryVersions.VersionDetails> versionDetails(
        String groupId, String artifactId, String version
    );

    @Override
    default Descriptor descriptor() {
        return Service.named("gateway")
            .withCalls(
                Service.restCall(Method.GET, "/api/v2/groups/:groupId", this::getGroup),
                Service.restCall(Method.GET, "/api/v2/groups", this::getGroups),
                Service.restCall(Method.POST, "/api/v2/groups", this::registerGroup),
                Service.restCall(Method.GET, "/api/v2/groups/:groupId/artifacts", this::getArtifacts),
                Service.restCall(Method.POST, "/api/v2/groups/:groupId/artifacts", this::registerArtifacts),
                Service.restCall(Method.GET, "/api/v2/groups/:groupId/artifacts/:artifactId", this::getArtifactDetails),
                Service.restCall(Method.POST, "/api/v2/auth/login", this::login),
                Service.restCall(Method.POST, "/api/v2/auth/logout", this::logout),
                Service.restCall(Method.POST, "/api/v2/groups/:groupId/artifacts/:artifactId/tags", this::registerArtifactTag),
                Service.restCall(Method.PATCH, "/api/v2/groups/:groupId/artifacts/:artifactId/tags", this::updateArtifactTag),
                Service.restCall(Method.POST, "/api/v2/groups/:groupId/artifacts/:artifactId/promotion", this::tagVersion),
                Service.restCall(Method.GET, "/api/v2/groups/:groupId/artifacts/:artifactId/versions?tags&limit&offset&recommended", this::artifactVersions),
                Service.restCall(Method.GET, "/api/v2/groups/:groupId/artifacts/:artifactId/versions/:version", this::versionDetails),
                Service.restCall(Method.GET, "/api/v2/groups/:groupId/artifacts/:artifactId/latest?tags&recommended", this::latestArtifact)
            )
            .withAutoAcl(true);
    }

}
