package org.spongepowered.downloads.versions.api.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.spongepowered.downloads.versions.api.models.tags.ArtifactTagEntry;

public interface TagRegistration {

    @JsonDeserialize
    final record Register(@JsonProperty("tag") ArtifactTagEntry entry) {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Response.TagAlreadyRegistered.class, name = "AlreadyRegistered"),
        @JsonSubTypes.Type(value = Response.TagSuccessfullyRegistered.class, name = "Success")
    })
    interface Response {

        final record TagAlreadyRegistered(@JsonProperty String name) implements TagRegistration.Response {}

        @JsonSerialize
        final record TagSuccessfullyRegistered() implements TagRegistration.Response {}

    }
}
