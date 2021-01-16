package org.spongepowered.downloads.artifact;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import org.pac4j.core.config.Config;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.auth.api.SOADAuth;
import org.spongepowered.downloads.utils.AuthUtils;

public class ArtifactModule extends AbstractModule implements ServiceGuiceSupport {

    @Override
    protected void configure() {
        this.bindService(ArtifactService.class, ArtifactServiceImpl.class);
    }

    @Provides
    @SOADAuth
    protected Config configProvider() {
        return AuthUtils.createConfig();
    }

}
