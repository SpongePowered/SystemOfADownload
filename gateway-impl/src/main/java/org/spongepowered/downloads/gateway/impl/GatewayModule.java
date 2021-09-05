package org.spongepowered.downloads.gateway.impl;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifacts.query.api.ArtifactQueryService;
import org.spongepowered.downloads.auth.api.AuthService;
import org.spongepowered.downloads.gateway.api.GatewayService;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.query.api.VersionsQueryService;

public class GatewayModule extends AbstractModule implements ServiceGuiceSupport {

    @Inject
    public GatewayModule() {}

    @Override
    protected void configure() {
        this.bindClient(ArtifactService.class);
        this.bindClient(ArtifactQueryService.class);
        this.bindClient(AuthService.class);
        this.bindClient(VersionsService.class);
        this.bindClient(VersionsQueryService.class);
        this.bindService(GatewayService.class, GatewayServiceImpl.class);
    }

}
