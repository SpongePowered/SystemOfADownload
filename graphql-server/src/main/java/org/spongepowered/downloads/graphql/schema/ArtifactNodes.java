/*
 * This file is part of SystemOfADownload, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://spongepowered.org/>
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

    private static GraphQLFieldDefinition makeStringField(final String fieldName, final String description) {
        return GraphQLFieldDefinition.newFieldDefinition()
            .name(fieldName)
            .description(description)
            .type(Scalars.GraphQLString)
            .build();
    }

}
