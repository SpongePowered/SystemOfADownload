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
import graphql.language.Description;
import graphql.language.ScalarTypeDefinition;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeRuntimeWiring;
import graphql.schema.visibility.GraphqlFieldVisibility;
import org.spongepowered.downloads.git.api.CommitSha;

public class CommitNodes {



    private static final String AUTHOR_TYPE_NAME = "Author";
    public static final GraphQLObjectType AUTHOR_TYPE = GraphQLObjectType.newObject()
        .name(AUTHOR_TYPE_NAME)
        .field(builder -> builder.name("Name").type(Scalars.GraphQLString))
        .field(b -> b.name("Email").type(Scalars.GraphQLString))
        .build();

    public static final GraphQLScalarType COMMIT_SHA_TYPE = GraphQLScalarType.newScalar()
        .name("CommitSha")
        .description("A 160-bit SHA-1 identifier for a commit in git. Representable and uniquely identifiable, " +
                "near likely impossible to have the same identifier for a commit within the same branch history. " +
                "As according to the")
        .specifiedByUrl("https://git-scm.com/book/en/v2/Git-Tools-Revision-Selection")
        .coercing(new Coercing<CommitSha, String>() {

            @Override
            public String serialize(final Object o) throws CoercingSerializeException {
                return null;
            }

            @Override
            public CommitSha parseValue(final Object o) throws CoercingParseValueException {
                return null;
            }

            @Override
            public CommitSha parseLiteral(final Object o) throws CoercingParseLiteralException {
                if (o instanceof String) {

                }
                return null;
            }
        })
        .build();

    public static final GraphQLFieldDefinition COMMIT_AUTHOR = GraphQLFieldDefinition.newFieldDefinition()
        .name("Author")
        .type(AUTHOR_TYPE)
        .description(
    "The commit author, may be different than the author that committed the commit. " +
        "Likewise, the committer may be the same as the author, in which case they may be " +
        "redundant."
        )
        .build();

    public static final GraphQLObjectType COMMIT_TYPE = GraphQLObjectType.newObject()
        .name("Commit")
        .field(COMMIT_AUTHOR)
        .build();

}
