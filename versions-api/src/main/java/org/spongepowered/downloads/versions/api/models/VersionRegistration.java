package org.spongepowered.downloads.versions.api.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.Jsonable;
import io.swagger.v3.oas.annotations.media.Schema;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;

import java.io.Serial;
import java.util.Objects;
import java.util.StringJoiner;

public final class VersionRegistration {


    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Register.Collection.class, name = "Collection"),
        @JsonSubTypes.Type(value = Register.Version.class, name = "Version"),
    })
    public interface Register {

        record Collection(
            @Schema(required = true) @JsonProperty ArtifactCollection collection)
            implements Register {

            @JsonCreator
            public Collection(@Schema(required = true) final ArtifactCollection collection) {
                this.collection = collection;
            }

        }

        @JsonDeserialize
        final class Version implements Register {
            @JsonProperty
            public final MavenCoordinates coordinates;

            @JsonCreator
            public Version(final MavenCoordinates coordinates) {
                this.coordinates = coordinates;
            }

            @Override
            public boolean equals(final Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                Version version = (Version) o;
                return Objects.equals(coordinates, version.coordinates);
            }

            @Override
            public int hashCode() {
                return Objects.hash(coordinates);
            }

            @Override
            public String toString() {
                return new StringJoiner(", ", Version.class.getSimpleName() + "[", "]")
                    .add("coordinates=" + coordinates)
                    .toString();
            }
        }


    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Response.GroupMissing.class, name = "GroupMissing"),
        @JsonSubTypes.Type(value = Response.ArtifactAlreadyRegistered.class, name = "AlreadyRegistered"),
        @JsonSubTypes.Type(value = Response.RegisteredArtifact.class, name = "Registered"),
    })
    public interface Response extends Jsonable {

        @JsonDeserialize
        record ArtifactAlreadyRegistered(
            @JsonProperty(required = true) ArtifactCoordinates coordinates
        )
            implements Response {

            @JsonCreator
            public ArtifactAlreadyRegistered(@JsonProperty(required = true) final ArtifactCoordinates coordinates) {
                this.coordinates = coordinates;
            }
        }

        @JsonDeserialize
        final class RegisteredArtifact implements Response {

            @Serial private static final long serialVersionUID = 9042959235854086148L;

            @JsonProperty(required = true)
            public final Map<String, Artifact> artifactComponents;

            @JsonProperty(value = "mavenCoordinates", required = true)
            public final MavenCoordinates coordinates;

            @JsonCreator
            public RegisteredArtifact(final MavenCoordinates mavenCoordinates) {
                this.coordinates = mavenCoordinates;
                this.artifactComponents = HashMap.empty();
            }

            @JsonCreator
            public RegisteredArtifact(
                final Map<String, Artifact> artifactComponents,
                final MavenCoordinates mavenCoordinates
            ) {
                this.artifactComponents = artifactComponents;
                this.coordinates = mavenCoordinates;
            }


        }

        @JsonDeserialize
        record GroupMissing(@JsonProperty(required = true) String groupId) implements Response {

        }
    }
}
