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

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.transport.Method;
import org.spongepowered.downloads.versions.api.models.ArtifactUpdate;
import org.spongepowered.downloads.versions.api.models.TagRegistration;
import org.spongepowered.downloads.versions.api.models.TagVersion;
import org.spongepowered.downloads.versions.api.models.VersionRegistration;
import org.spongepowered.downloads.versions.api.models.VersionedArtifactUpdates;

public interface VersionsService extends Service {

    ServiceCall<VersionRegistration.Register, VersionRegistration.Response> registerArtifactCollection(
        String groupId, String artifactId
    );

    ServiceCall<TagRegistration.Register, TagRegistration.Response> registerArtifactTag(String groupId, String artifactId);
    ServiceCall<TagRegistration.Register, TagRegistration.Response> updateArtifactTag(String groupId, String artifactId);

    ServiceCall<TagVersion.Request, TagVersion.Response> tagVersion(String groupId, String artifactId);

    Topic<ArtifactUpdate> artifactUpdateTopic();

    Topic<VersionedArtifactUpdates> versionedArtifactUpdatesTopic();

    @Override
    default Descriptor descriptor() {
        return Service.named("versions")
            .withCalls(
                Service.restCall(Method.POST, "/versions/groups/:groupId/artifacts/:artifactId/versions", this::registerArtifactCollection),
                Service.restCall(Method.POST, "/versions/groups/:groupId/artifacts/:artifactId/tags", this::registerArtifactTag),
                Service.restCall(Method.PATCH, "/versions/groups/:groupId/artifacts/:artifactId/tags", this::updateArtifactTag),
                Service.restCall(Method.POST, "/versions/groups/:groupId/artifacts/:artifactId/promotion", this::tagVersion)
             )
            .withTopics(
                Service.topic("artifact-update", this::artifactUpdateTopic),
                Service.topic("versioned-artifact-updates", this::versionedArtifactUpdatesTopic)
            )
            .withAutoAcl(true);
    }

}
