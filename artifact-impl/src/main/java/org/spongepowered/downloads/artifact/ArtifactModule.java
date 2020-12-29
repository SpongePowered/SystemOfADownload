package org.spongepowered.downloads.artifact;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.auth.api.AuthService;

public class ArtifactModule extends AbstractModule implements ServiceGuiceSupport {

    @Override
    protected void configure() {
        this.bindClient(AuthService.class);
        this.bindService(ArtifactService.class, ArtifactServiceImpl.class);
    }
}
