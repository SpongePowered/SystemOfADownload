package org.spongepowered.downloads.graphql;


import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class GraphQLRequest {

    private final String query;
    @Nullable private final String operationName;
    @Nullable private final Map<String, Object> variables;

    /**
     * Creates a new GraphQLRequest based on the provided details.
     *
     * @param query The query of the graphql request
     * @param operationName The operation name, this is if there's multiple operations
     * @param variables The variables of the query
     */
    public GraphQLRequest(final String query, final @Nullable String operationName,
        final @Nullable Map<String, Object> variables) {
        this.query = query;
        this.operationName = operationName;
        this.variables = variables;
    }

    public String getQuery() {
        return this.query;
    }

    public Optional<String> getOperationName() {
        return Optional.ofNullable(this.operationName);
    }

    public Optional<Map<String, Object>> getVariables() {
        return Optional.ofNullable(this.variables);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final GraphQLRequest that = (GraphQLRequest) o;
        return Objects.equals(this.query, that.query) &&
            Objects.equals(this.operationName, that.operationName) &&
            Objects.equals(this.variables, that.variables);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.query, this.operationName, this.variables);
    }
}
