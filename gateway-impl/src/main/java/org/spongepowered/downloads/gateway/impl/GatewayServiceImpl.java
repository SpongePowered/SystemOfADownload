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
package org.spongepowered.downloads.gateway.impl;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.server.HeaderServiceCall;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.artifact.api.query.GroupsResponse;
import org.spongepowered.downloads.artifacts.query.api.ArtifactQueryService;
import org.spongepowered.downloads.artifacts.query.api.GetArtifactDetailsResponse;
import org.spongepowered.downloads.auth.api.AuthService;
import org.spongepowered.downloads.auth.api.AuthenticationRequest;
import org.spongepowered.downloads.gateway.api.GatewayService;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.api.models.TagRegistration;
import org.spongepowered.downloads.versions.api.models.TagVersion;
import org.spongepowered.downloads.versions.api.models.VersionRegistration;
import org.spongepowered.downloads.versions.query.api.VersionsQueryService;
import org.spongepowered.downloads.versions.query.api.models.QueryVersions;

import javax.inject.Inject;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

public record GatewayServiceImpl(
    ArtifactService artifactService,
    ArtifactQueryService artifactQueryService,
    AuthService authService,
    VersionsService versionsService,
    VersionsQueryService versionsQueryService
) implements GatewayService {

    @Inject
    public GatewayServiceImpl {
    }

    /*
    A pure function that's designed to pass along and be interfaced as the "front-end" service to interact
    with the API. This is basically a glorified router, but acts as a L7 gateway service.
     */
    private static <Req, Resp> Function<ServiceCall<Req, Resp>, ServiceCall<Req, Resp>> passHeaders() {
        return call -> HeaderServiceCall.compose(header -> req -> call
            .handleRequestHeader(requestHeader -> requestHeader.replaceAllHeaders(header.headers()))
            .invoke(req)
        );
    }

    @Override
    public ServiceCall<NotUsed, GetArtifactsResponse> getArtifacts(
        final String groupId
    ) {
        return GatewayServiceImpl.<NotUsed, GetArtifactsResponse>passHeaders().apply(this.artifactService.getArtifacts(groupId));
    }

    @Override
    public ServiceCall<ArtifactRegistration.RegisterArtifact, ArtifactRegistration.Response> registerArtifacts(
        final String groupId
    ) {
        return GatewayServiceImpl.<ArtifactRegistration.RegisterArtifact, ArtifactRegistration.Response>passHeaders()
            .apply(this.artifactService.registerArtifacts(groupId.toLowerCase(Locale.ROOT).trim()));
    }

    @Override
    public ServiceCall<GroupRegistration.RegisterGroupRequest, GroupRegistration.Response> registerGroup() {
        return GatewayServiceImpl.<GroupRegistration.RegisterGroupRequest, GroupRegistration.Response>passHeaders()
            .apply(this.artifactService.registerGroup());
    }

    @Override
    public ServiceCall<NotUsed, GroupResponse> getGroup(
        final String groupId
    ) {
        return GatewayServiceImpl.<NotUsed, GroupResponse>passHeaders()
            .apply(this.artifactService.getGroup(groupId));
    }

    @Override
    public ServiceCall<NotUsed, GroupsResponse> getGroups() {
        return GatewayServiceImpl.<NotUsed, GroupsResponse>passHeaders()
            .apply(this.artifactService.getGroups());
    }

    @Override
    public ServiceCall<NotUsed, GetArtifactDetailsResponse> getArtifactDetails(
        final String groupId,
        final String artifactId
    ) {
        return GatewayServiceImpl.<NotUsed, GetArtifactDetailsResponse>passHeaders()
            .apply(this.artifactQueryService.getArtifactDetails(groupId, artifactId));
    }

    @Override
    public ServiceCall<NotUsed, AuthenticationRequest.Response> login() {
        return GatewayServiceImpl.<NotUsed, AuthenticationRequest.Response>passHeaders()
            .apply(this.authService.login());
    }

    @Override
    public ServiceCall<NotUsed, NotUsed> logout() {
        return GatewayServiceImpl.<NotUsed, NotUsed>passHeaders()
            .apply(this.authService.logout());
    }

    @Override
    public ServiceCall<VersionRegistration.Register, VersionRegistration.Response> registerArtifactCollection(
        final String groupId,
        final String artifactId
    ) {
        return GatewayServiceImpl.<VersionRegistration.Register, VersionRegistration.Response>passHeaders()
            .apply(this.versionsService.registerArtifactCollection(groupId, artifactId));
    }

    @Override
    public ServiceCall<TagRegistration.Register, TagRegistration.Response> registerArtifactTag(
        final String groupId,
        final String artifactId
    ) {
        return GatewayServiceImpl.<TagRegistration.Register, TagRegistration.Response>passHeaders()
            .apply(this.versionsService.registerArtifactTag(groupId, artifactId));
    }

    @Override
    public ServiceCall<TagRegistration.Register, TagRegistration.Response> updateArtifactTag(
        final String groupId, final String artifactId
    ) {
        return GatewayServiceImpl.<TagRegistration.Register, TagRegistration.Response>passHeaders()
            .apply(this.versionsService.updateArtifactTag(groupId, artifactId));
    }

    @Override
    public ServiceCall<TagVersion.Request, TagVersion.Response> tagVersion(
        final String groupId,
        final String artifactId
    ) {
        return GatewayServiceImpl.<TagVersion.Request, TagVersion.Response>passHeaders()
            .apply(this.versionsService.tagVersion(groupId, artifactId));
    }

    @Override
    public ServiceCall<NotUsed, QueryVersions.VersionInfo> artifactVersions(
        final String groupId,
        final String artifactId,
        final Optional<String> tags,
        final Optional<Integer> limit,
        final Optional<Integer> offset,
        final Optional<Boolean> recommended
    ) {
        return GatewayServiceImpl.<NotUsed, QueryVersions.VersionInfo>passHeaders()
            .apply(this.versionsQueryService.artifactVersions(groupId, artifactId, tags, limit, offset, recommended));
    }

    @Override
    public ServiceCall<NotUsed, QueryVersions.VersionDetails> latestArtifact(
        final String groupId, final String artifactId, final Optional<String> tags,
        final Optional<Boolean> recommended
    ) {
        return GatewayServiceImpl.<NotUsed, QueryVersions.VersionDetails>passHeaders()
            .apply(this.versionsQueryService.latestArtifact(groupId, artifactId, tags, recommended));
    }

    @Override
    public ServiceCall<NotUsed, QueryVersions.VersionDetails> versionDetails(
        final String groupId, final String artifactId, final String version
    ) {
        return GatewayServiceImpl.<NotUsed, QueryVersions.VersionDetails>passHeaders()
            .apply(this.versionsQueryService.versionDetails(groupId, artifactId, version));
    }
}
