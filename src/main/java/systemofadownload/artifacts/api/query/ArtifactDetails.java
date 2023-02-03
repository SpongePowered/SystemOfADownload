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
package systemofadownload.artifacts.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.lightbend.lagom.javadsl.api.transport.BadRequest;
import io.vavr.control.Either;
import io.vavr.control.Try;

import java.net.URL;

public final class ArtifactDetails {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Update.Website.class,
            name = "website"),
        @JsonSubTypes.Type(value = Update.DisplayName.class,
            name = "displayName"),
        @JsonSubTypes.Type(value = Update.Issues.class,
            name = "issues"),
        @JsonSubTypes.Type(value = Update.GitRepository.class,
            name = "gitRepository"),
    })
    @JsonDeserialize
    public sealed interface Update<T> {

        Either<BadRequest, T> validate();

        record Website(
            @JsonProperty(required = true) String website
        ) implements Update<URL> {

            @JsonCreator
            public Website {
            }

            @Override
            public Either<BadRequest, URL> validate() {
                return Try.of(() -> new URL(this.website()))
                    .toEither(() -> new BadRequest(String.format("Malformed url: %s", this.website())));
            }
        }

        record DisplayName(
            @JsonProperty(required = true) String display
        ) implements Update<String> {

            @JsonCreator
            public DisplayName {
            }

            @Override
            public Either<BadRequest, String> validate() {
                return Either.right(this.display.trim());
            }
        }

        record Issues(
            @JsonProperty(required = true) String issues
        ) implements Update<URL> {
            @JsonCreator
            public Issues {
            }

            @Override
            public Either<BadRequest, URL> validate() {
                return Try.of(() -> new URL(this.issues()))
                    .toEither(() -> new BadRequest(String.format("Malformed url: %s", this.issues())));
            }
        }

        record GitRepository(
            @JsonProperty(required = true) String gitRepo
        ) implements Update<URL> {

            @JsonCreator
            public GitRepository {
            }

            @Override
            public Either<BadRequest, URL> validate() {
                return Try.of(() -> new URL(this.gitRepo()))
                    .toEither(() -> new BadRequest(String.format("Malformed url: %s", this.gitRepo())));
            }
        }
    }

    @JsonSerialize
    public record Response(
        String name,
        String displayName,
        String website,
        String issues,
        String gitRepo
    ) {

    }


}
