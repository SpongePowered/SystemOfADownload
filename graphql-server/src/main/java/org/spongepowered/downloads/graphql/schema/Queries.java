package org.spongepowered.downloads.graphql.schema;

import graphql.Scalars;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;

public class Queries {


    private static final GraphQLFieldDefinition COMMIT_NODE = GraphQLFieldDefinition
        .newFieldDefinition()
        .name("field")
        .type(Scalars.GraphQLString)
        .build();


    public static final GraphQLObjectType QUERY_TYPE = GraphQLObjectType.newObject()
        .name("Query")
        .field(COMMIT_NODE)
        .build();
}
