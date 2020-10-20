package org.spongepowered.downloads.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import graphql.schema.GraphQLSchema;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.spongepowered.downloads.git.api.CommitService;
import org.spongepowered.downloads.graphql.schema.Queries;

import java.util.concurrent.CompletableFuture;

/**
 * Not an official service of any kind, generates and provides a graphql server
 * schema
 */
public class GraphQLService implements Service {

    private static final Logger LOGGER = LogManager.getLogger("GraphQLServer");
    private final CommitService commitService;
    private final GraphQLSchema schema;

    @Inject
    public GraphQLService(final CommitService commitService) {
        this.commitService = commitService;
        this.schema = GraphQLSchema.newSchema()
            .query(Queries.QUERY_TYPE)
            .mutation()
            .build();
    }

    private ServiceCall<JsonNode, JsonNode> graphql() {
        return request -> {
            // This isn't how it'll work, but sure, this is basically where the
            return CompletableFuture.completedFuture(request);
        };
    }

    @Override
    public Descriptor descriptor() {
        return Service.named("graphql")
            .withCalls(
                // GraphQL effectively will be on the path, and accept any json blob
                Service.restCall(Method.POST, "/graphql", this::graphql)
            );
    }
}
