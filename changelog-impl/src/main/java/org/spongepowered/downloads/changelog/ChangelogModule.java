package org.spongepowered.downloads.changelog;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.changelog.api.ChangelogService;
import org.spongepowered.downloads.git.api.CommitService;

public class ChangelogModule extends AbstractModule implements ServiceGuiceSupport {

    @Override
    protected void configure() {
        this.bindService(ChangelogService.class, ChangelogServiceImpl.class);
        this.bind(ArtifactReadSideProcessor.class);
        this.bindClient(ArtifactService.class);
        this.bindClient(CommitService.class);
    }
}
