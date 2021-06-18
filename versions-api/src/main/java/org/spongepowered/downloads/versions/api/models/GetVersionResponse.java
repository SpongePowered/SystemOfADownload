package org.spongepowered.downloads.versions.api.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
    @JsonSubTypes.Type(
        value = GetVersionResponse.VersionInfo.class,
        name = "Version"
    ),
    @JsonSubTypes.Type(
        value = GetVersionResponse.VersionMissing.class,
        name = "Missing"
    )
})
public interface GetVersionResponse {

    @JsonSerialize
    final record VersionInfo(@JsonProperty("components") ArtifactCollection collection) implements GetVersionResponse {
    }
    @JsonSerialize
    final record VersionMissing(@JsonProperty String version) implements GetVersionResponse { }
}
