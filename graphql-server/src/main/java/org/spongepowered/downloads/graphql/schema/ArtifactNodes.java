package org.spongepowered.downloads.graphql.schema;

import graphql.Scalars;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLUnionType;

public class ArtifactNodes {

    public static final GraphQLObjectType EXISTING_ARTIFACT_TYPE = GraphQLObjectType.newObject()
        .name("Artifact")
        .build();


    public static final GraphQLFieldDefinition ARTIFACT_MODULE_NAME = makeStringField("moduleId", "The Maven module compatible id for the artifact");

    public static final GraphQLObjectType MISSING_ARTIFACT_TYPE = GraphQLObjectType.newObject()
        .name("MissingArtifact")
        .description("An artifact deemed missing or unavailable")
        .field(ARTIFACT_MODULE_NAME)
        .build();
    public static final GraphQLUnionType ARTIFACT_UNION_TYPE = GraphQLUnionType.newUnionType()
        .name("Artifact")
        .possibleType(EXISTING_ARTIFACT_TYPE)
        .possibleType(MISSING_ARTIFACT_TYPE)
        .description("Union type presenting an always available non-null type, whether it's unavailable, missing, etc.")
        .build();

    private static GraphQLFieldDefinition makeStringField(String fieldName, String description) {
        return GraphQLFieldDefinition.newFieldDefinition()
            .name(fieldName)
            .description(description)
            .type(Scalars.GraphQLString)
            .build();
    }

}
