/*
 * This file is part of SystemOfADownload, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://spongepowered.org/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.downloads.versions.api;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.broker.kafka.KafkaProperties;
import com.lightbend.lagom.javadsl.api.transport.Method;
import org.spongepowered.downloads.versions.api.event.VersionedArtifactEvent;
import org.spongepowered.downloads.versions.api.models.GetVersionResponse;
import org.spongepowered.downloads.versions.api.models.GetVersionsResponse;
import org.spongepowered.downloads.versions.api.models.TagRegistration;
import org.spongepowered.downloads.versions.api.models.TagVersion;
import org.spongepowered.downloads.versions.api.models.VersionRegistration;

import java.util.Optional;

public interface VersionsService extends Service {

    ServiceCall<NotUsed, GetVersionsResponse> getArtifactVersions(
        String groupId, String artifactId, Optional<String> tags, Optional<Integer> limit,
        Optional<Integer> offset, Optional<Boolean> recommended
    );

    ServiceCall<NotUsed, GetVersionResponse> getArtifactVersion(String groupId, String artifactId, String version);

    ServiceCall<VersionRegistration.Register, VersionRegistration.Response> registerArtifactCollection(
        String groupId, String artifactId
    );

    ServiceCall<TagRegistration.Register, TagRegistration.Response> registerArtifactTag(String groupId, String artifactId);
    ServiceCall<TagRegistration.Register, TagRegistration.Response> updateArtifactTag(String groupId, String artifactId);

    ServiceCall<TagVersion.Request, TagVersion.Response> tagVersion(String groupId, String artifactId);

    Topic<VersionedArtifactEvent> topic();

    @Override
    default Descriptor descriptor() {
        return Service.named("versions")
            .withCalls(
                Service.restCall(Method.GET, "/api/v2/groups/:groupId/artifacts/:artifactId/versions?tags&limit&offset&recommended", this::getArtifactVersions),
                Service.restCall(Method.POST, "/api/v2/groups/:groupId/artifacts/:artifactId/versions", this::registerArtifactCollection),
                Service.restCall(Method.POST, "/api/v2/groups/:groupId/artifacts/:artifactId/tags", this::registerArtifactTag),
                Service.restCall(Method.PATCH, "/api/v2/groups/:groupId/artifacts/:artifactId/tags", this::updateArtifactTag),
                Service.restCall(Method.GET, "/api/v2/groups/:groupId/artifacts/:artifactId/versions/:version", this::getArtifactVersion),
                Service.restCall(Method.POST, "/api/v2/groups/:groupId/artifacts/:artifactId/promotion", this::tagVersion)
             )
            .withTopics(
                Service.topic("versioned_artifact_activity", this::topic)
                    .withProperty(KafkaProperties.partitionKeyStrategy(), VersionedArtifactEvent::asMavenCoordinates)
            )
            .withAutoAcl(true);
    }

}
