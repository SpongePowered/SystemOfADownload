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
package org.spongepowered.downloads.artifact.api;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.api.broker.kafka.KafkaProperties;
import com.lightbend.lagom.javadsl.api.transport.Method;
import org.spongepowered.downloads.artifact.api.event.GroupUpdate;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.artifact.api.query.GroupsResponse;

public interface ArtifactService extends Service {

    ServiceCall<NotUsed, GetArtifactsResponse> getArtifacts(String groupId);

    ServiceCall<ArtifactRegistration.RegisterArtifact, ArtifactRegistration.Response> registerArtifacts(
        String groupId
    );

    ServiceCall<GroupRegistration.RegisterGroupRequest, GroupRegistration.Response> registerGroup();

    ServiceCall<NotUsed, GroupResponse> getGroup(String groupId);

    ServiceCall<NotUsed, GroupsResponse> getGroups();

    Topic<GroupUpdate> groupTopic();

    @Override
    default Descriptor descriptor() {
        return Service.named("artifacts")
            .withCalls(
                Service.restCall(Method.GET, "/artifacts/groups/:groupId", this::getGroup),
                Service.restCall(Method.GET, "/artifacts/groups", this::getGroups),
                Service.restCall(Method.POST, "/artifacts/groups", this::registerGroup),
                Service.restCall(Method.GET, "/artifacts/groups/:groupId/artifacts", this::getArtifacts),
                Service.restCall(Method.POST, "/artifacts/groups/:groupId/artifacts", this::registerArtifacts)
            )
            .withTopics(
                Service.topic("group-activity", this::groupTopic)
                    .withProperty(KafkaProperties.partitionKeyStrategy(), GroupUpdate::groupId)
            )
            .withAutoAcl(true);
    }

}
