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
package systemofadownload.artifacts.api;

import akka.NotUsed;
import systemofadownload.artifacts.api.event.ArtifactUpdate;
import systemofadownload.artifacts.api.event.GroupUpdate;
import systemofadownload.artifacts.api.query.ArtifactDetails;
import systemofadownload.artifacts.api.query.ArtifactRegistration;
import systemofadownload.artifacts.api.query.GetArtifactsResponse;
import systemofadownload.artifacts.api.query.GroupRegistration;
import systemofadownload.artifacts.api.query.GroupResponse;
import systemofadownload.artifacts.api.query.GroupsResponse;

public interface ArtifactService {

    ServiceCall<ArtifactDetails.Update<?>, ArtifactDetails.Response> updateDetails(String groupId, String artifactId);

    ServiceCall<NotUsed, GroupResponse> getGroup(String groupId);

    ServiceCall<NotUsed, GroupsResponse> getGroups();

    Topic<GroupUpdate> groupTopic();

    Topic<ArtifactUpdate> artifactUpdate();

    @Override
    default Descriptor descriptor() {
        return Service.named("artifacts")
            .withCalls(
                Service.restCall(Method.GET, "/artifacts/groups/:groupId", this::getGroup),
                Service.restCall(Method.GET, "/artifacts/groups", this::getGroups),
                Service.restCall(Method.POST, "/artifacts/groups", this::registerGroup),
                Service.restCall(Method.GET, "/artifacts/groups/:groupId/artifacts", this::getArtifacts),
                Service.restCall(Method.POST, "/artifacts/groups/:groupId/artifacts", this::registerArtifacts),
                Service.restCall(Method.PATCH, "/artifacts/groups/:groupId/artifacts/:artifactId/update", this::updateDetails)
            )
            .withTopics(
                Service.topic("group-activity", this::groupTopic)
                    .withProperty(KafkaProperties.partitionKeyStrategy(), GroupUpdate::groupId),
                Service.topic("artifact-details-update", this::artifactUpdate)
                    .withProperty(KafkaProperties.partitionKeyStrategy(), ArtifactUpdate::partitionKey)
            )
            .withAutoAcl(true);
    }

}
