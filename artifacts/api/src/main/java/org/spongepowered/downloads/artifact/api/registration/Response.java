package org.spongepowered.downloads.artifact.api.registration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
    property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Response.GroupMissing.class,
        name = "UnknownGroup"),
    @JsonSubTypes.Type(value = Response.ArtifactRegistered.class,
        name = "RegisteredArtifact"),
    @JsonSubTypes.Type(value = Response.ArtifactAlreadyRegistered.class,
        name = "AlreadyRegistered"),
})
public sealed interface Response {

    @JsonSerialize
    record ArtifactRegistered(@JsonProperty ArtifactCoordinates coordinates) implements Response {

        @JsonIgnore
        public String groupId() {
            return this.coordinates.groupId();
        }

        @JsonIgnore
        public String artifactId() {
            return this.coordinates.artifactId();
        }

    }

    @JsonSerialize
    record ArtifactAlreadyRegistered(
        @JsonProperty String artifactName,
        @JsonProperty String groupId
    ) implements Response {

    }

    @JsonSerialize
    record GroupMissing(@JsonProperty("groupId") String s) implements Response {

    }

}
