package org.spongepowered.downloads.artifact.api.mutation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.vavr.control.Either;
import io.vavr.control.Try;
import org.spongepowered.downloads.artifact.api.query.ArtifactDetails;

import java.net.URI;
import java.net.URL;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
    property = "type")
@JsonDeserialize
public sealed interface Update<T> {

    Either<ArtifactDetails.Response.Error, T> validate();

    @JsonTypeName("website")
    record Website(
        @JsonProperty(required = true) String website
    ) implements Update<URL> {

        @Override
        public Either<ArtifactDetails.Response.Error, URL> validate() {
            return Try.of(() -> URI.create(this.website))
                .mapTry(URI::toURL)
                .toEither()
                .mapLeft(_ -> new ArtifactDetails.Response.Error(String.format("Invalid URL: %s", this.website)));
        }
    }

    @JsonTypeName("displayName")
    record DisplayName(
        @JsonProperty(required = true) String display
    ) implements Update<String> {

        @Override
        public Either<ArtifactDetails.Response.Error, String> validate() {
            return Either.right(this.display);
        }

    }

    @JsonTypeName("issues")
    record Issues(
        @JsonProperty(required = true) String issues
    ) implements Update<URL> {

            @Override
            public Either<ArtifactDetails.Response.Error, URL> validate() {
                return Try.of(() -> URI.create(this.issues))
                    .mapTry(URI::toURL)
                    .toEither()
                    .mapLeft(_ -> new ArtifactDetails.Response.Error(String.format("Invalid URL: %s", this.issues)));
            }
    }

    @JsonTypeName("git-repo")
    record GitRepository(
        @JsonProperty(required = true) String gitRepo
    ) implements Update<URL> {

        @Override
        public Either<ArtifactDetails.Response.Error, URL> validate() {
            return Try.of(() -> URI.create(this.gitRepo))
                .mapTry(URI::toURL)
                .toEither()
                .mapLeft(_ -> new ArtifactDetails.Response.Error(String.format("Invalid URL: %s", this.gitRepo)));
        }

    }
}
