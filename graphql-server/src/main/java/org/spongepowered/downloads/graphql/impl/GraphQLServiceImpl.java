package org.spongepowered.downloads.graphql.impl;

import akka.NotUsed;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.spongepowered.downloads.git.api.CommitService;
import org.spongepowered.downloads.graphql.api.GraphQLService;
import org.spongepowered.downloads.graphql.schema.Queries;
import org.taymyr.lagom.javadsl.openapi.AbstractOpenAPIService;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Not an official service of any kind, generates and provides a graphql server
 * schema
 */
public class GraphQLServiceImpl extends AbstractOpenAPIService implements GraphQLService {

    private static final Logger LOGGER = LogManager.getLogger("GraphQLServer");
    private static final Marker SERVICE = MarkerManager.getMarker("GRAPHQL");
    private final CommitService commitService;
    private final GraphQLSchema schema;
    private final GraphQL gql;

    @Inject
    public GraphQLServiceImpl(
        final CommitService commitService
    ) {
        this.commitService = commitService;
        this.schema = GraphQLSchema.newSchema()
            .query(Queries.QUERY_TYPE)
            .build();
        this.gql = GraphQL.newGraphQL(this.schema).build();
    }

    public ServiceCall<String, Map<String, Object>> graphql() {
        return request -> CompletableFuture.supplyAsync(() -> {
            LOGGER.log(Level.DEBUG, SERVICE, "Processing Request {}", request);
            return this.gql.execute(request);
        })
            .thenApply(ExecutionResult::toSpecification);
    }

    public ServiceCall<NotUsed, Map<String, Object>> graphqlByGet(final String queryJson) {
        return request -> CompletableFuture.supplyAsync(() -> this.gql.execute(queryJson))
            .thenApply(ExecutionResult::toSpecification);
    }
}
