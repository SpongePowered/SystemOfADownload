package org.spongepowered.downloads.graphql.impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.api.ServiceInfo;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.git.api.CommitService;
import org.spongepowered.downloads.graphql.api.GraphQLService;

public class GraphQLServerModule extends AbstractModule implements ServiceGuiceSupport {

    @Override
    protected void configure() {
        this.bindClient(CommitService.class);
        this.bindClient(ArtifactService.class);
        this.bindService(GraphQLService.class, GraphQLServiceImpl.class);
    }
}
