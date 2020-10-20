package org.spongepowered.downloads.git;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import org.spongepowered.downloads.git.api.CommitService;

public class CommitModule extends AbstractModule implements ServiceGuiceSupport {

    @Override
    protected void configure() {
        this.bindService(CommitService.class, CommitServiceImpl.class);
    }
}
