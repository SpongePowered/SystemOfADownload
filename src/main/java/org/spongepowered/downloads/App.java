/*
 * This file is part of SystemOfADownload, licensed under the MIT License (MIT).
 *
 * Copyright (c) "SpongePowered" <"https://www.spongepowered.org/">
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.downloads;

import com.google.gson.Gson;
import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import org.spongepowered.downloads.graphql.GraphQLRequest;
import spark.Spark;

public class App {
    private final Gson gson;

    App() {
        this.gson = new Gson();
    }

    /**
     * Entrypoint for the app. Does
     *
     * @param args entrypoint args
     */
    public static void main(final String[] args) {
        final App app = new App();
        Spark.post("/graphql", (request, response) -> {
            response.type("application/json");
            final String body = request.body();
            final GraphQLRequest graphQLRequest = app.gson.fromJson(body, GraphQLRequest.class);
            final GraphQLSchema.Builder schemaBuilder = GraphQLSchema.newSchema();
            final GraphQLSchema schema = schemaBuilder.build();
            final GraphQL graphQL = GraphQL.newGraphQL(schema).build();

            final ExecutionInput.Builder builder = ExecutionInput.newExecutionInput()
                .query(graphQLRequest.getQuery());
            graphQLRequest.getVariables().ifPresent(builder::variables);
            graphQLRequest.getOperationName().ifPresent(builder::operationName);
            return graphQL.execute(builder);
        });
    }
}
