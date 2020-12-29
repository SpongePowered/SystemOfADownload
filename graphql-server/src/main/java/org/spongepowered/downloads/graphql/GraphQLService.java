package org.spongepowered.downloads.graphql;

import akka.NotUsed;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.spongepowered.downloads.git.api.CommitService;
import org.spongepowered.downloads.graphql.schema.Queries;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Not an official service of any kind, generates and provides a graphql server
 * schema
 */
public class GraphQLService implements Service {

    private static final Logger LOGGER = LogManager.getLogger("GraphQLServer");
    private static final Marker SERVICE = MarkerManager.getMarker("GRAPHQL");
    private final CommitService commitService;
    private final GraphQLSchema schema;
    private final GraphQL gql;

    @Inject
    public GraphQLService(final CommitService commitService) {
        this.commitService = commitService;
        this.schema = GraphQLSchema.newSchema()
            .query(Queries.QUERY_TYPE)
            .build();
        this.gql = GraphQL.newGraphQL(this.schema).build();
    }

    private ServiceCall<String, Map<String, Object>> graphql() {
        return request -> CompletableFuture.supplyAsync(() -> {
            LOGGER.log(Level.DEBUG, SERVICE, "Processing Request {}", request);
            return this.gql.execute(request);
        })
            .thenApply(ExecutionResult::toSpecification);
    }

    private ServiceCall<NotUsed, Map<String, Object>> graphqlByGet(final String queryJson) {
        return request -> CompletableFuture.supplyAsync(() -> this.gql.execute(queryJson))
            .thenApply(ExecutionResult::toSpecification);
    }

    @Override
    public Descriptor descriptor() {
        return Service.named("graphql")
            .withCalls(
                // GraphQL effectively will be on the path, and accept any json blob
                Service.restCall(Method.POST, "/graphql", this::graphql),
                Service.restCall(Method.GET, "/graphql", this::graphqlByGet)
            )
            .withAutoAcl(true);
    }
}
