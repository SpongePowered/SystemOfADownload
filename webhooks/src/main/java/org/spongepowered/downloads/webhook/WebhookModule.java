package org.spongepowered.downloads.webhook;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.changelog.api.ChangelogService;
import org.spongepowered.downloads.git.api.CommitService;
import play.libs.akka.AkkaGuiceSupport;

public class WebhookModule extends AbstractModule implements ServiceGuiceSupport {

    @Override
    protected void configure() {
        this.bindClient(CommitService.class);
        this.bindClient(ArtifactService.class);
        this.bindClient(ChangelogService.class);
        this.bindService(SonatypeWebhookService.class, SonatypeWebhookService.class);
        this.bindService(SonatypeArtifactWorkerService.class, SonatypeArtifactWorkerService.class);
    }
}
