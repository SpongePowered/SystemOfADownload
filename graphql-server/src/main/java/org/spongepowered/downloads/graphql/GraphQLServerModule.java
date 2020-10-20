package org.spongepowered.downloads.graphql;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import org.spongepowered.downloads.git.api.CommitService;

public class GraphQLServerModule extends AbstractModule implements ServiceGuiceSupport {

    @Override
    protected void configure() {
        this.bindService(GraphQLService.class, GraphQLService.class);
        this.bindClient(CommitService.class);
    }
}
