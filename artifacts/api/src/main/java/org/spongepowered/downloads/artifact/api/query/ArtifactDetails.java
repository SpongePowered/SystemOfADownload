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
package org.spongepowered.downloads.artifact.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.net.URL;

public final class ArtifactDetails {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        property = "type")
    @JsonDeserialize
    public sealed interface Update<T> {

        @JsonTypeName("website")
        record Website(
            @JsonProperty(required = true) String website
        ) implements Update<URL> {

            @JsonCreator
            public Website {
            }

        }

        @JsonTypeName("displayName")
        record DisplayName(
            @JsonProperty(required = true) String display
        ) implements Update<String> {

            @JsonCreator
            public DisplayName {
            }

        }

        @JsonTypeName("issues")
        record Issues(
            @JsonProperty(required = true) String issues
        ) implements Update<URL> {
            @JsonCreator
            public Issues {
            }

        }

        @JsonTypeName("git-repo")
        record GitRepository(
            @JsonProperty(required = true) String gitRepo
        ) implements Update<URL> {

            @JsonCreator
            public GitRepository {
            }

        }
    }

    @JsonSerialize
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    public sealed interface Response {

        record Ok(
            String name,
            String displayName,
            String website,
            String issues,
            String gitRepo
        )  implements Response{

        }

        record NotFound(String message) implements Response {}
    }



}
