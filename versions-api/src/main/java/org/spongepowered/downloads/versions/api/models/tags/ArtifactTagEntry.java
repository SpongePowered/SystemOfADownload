package org.spongepowered.downloads.versions.api.models.tags;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize
public record ArtifactTagEntry(
    @JsonProperty(required = true) String name,
    @JsonProperty(required = true) int matchingGroup,
    @JsonProperty(required = true) String regex
) {

    @JsonCreator
    public ArtifactTagEntry(
        @JsonProperty(required = true) String name,
        @JsonProperty(required = true) int matchingGroup,
        @JsonProperty(required = true) String regex
    ) {
        this.name = name;
        this.matchingGroup = matchingGroup;
        this.regex = regex;
    }
}
