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

        final class Collection implements Register {

            @Schema(required = true) @JsonProperty
            public final ArtifactCollection collection;

            @JsonCreator
            public Collection(@Schema(required = true) final ArtifactCollection collection) {
                this.collection = collection;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (Collection) obj;
                return Objects.equals(this.collection, that.collection);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.collection);
            }

            @Override
            public String toString() {
                return "RegisterCollection[" +
                    "collection=" + this.collection + ']';
            }
        }

        final class Version implements Register {
            public final MavenCoordinates coordinates;

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
        final class ArtifactAlreadyRegistered implements Response {

            @JsonProperty(required = true)
            public final ArtifactCoordinates coordinates;

            public ArtifactAlreadyRegistered(ArtifactCoordinates coordinates) {
                this.coordinates = coordinates;
            }

            @Override
            public String toString() {
                return new StringJoiner(", ", ArtifactAlreadyRegistered.class.getSimpleName() + "[", "]")
                    .add("coordinates=" + coordinates)
                    .toString();
            }

            @Override
            public boolean equals(final Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                ArtifactAlreadyRegistered that = (ArtifactAlreadyRegistered) o;
                return Objects.equals(coordinates, that.coordinates);
            }

            @Override
            public int hashCode() {
                return Objects.hash(coordinates);
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
        final class GroupMissing implements Response {

            @JsonProperty(required = true)
            public final String groupId;

            public GroupMissing(String groupId) {
                this.groupId = groupId;
            }
        }
    }
}
