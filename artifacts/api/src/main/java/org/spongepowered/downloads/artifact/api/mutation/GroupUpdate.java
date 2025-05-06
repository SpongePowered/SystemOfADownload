package org.spongepowered.downloads.artifact.api.mutation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public sealed interface GroupUpdate {

    @JsonTypeName("website")
    @Introspected
    record Website(
        @Pattern(regexp = Update.URL_REGEX,
            message = "Invalid URL format")
        @JsonProperty(required = true)
        String website
    ) implements GroupUpdate {

    }

    @JsonTypeName("displayName")
    @Introspected
    record DisplayName(
        @NotBlank
        @Size(min = 1, max = 255)
        @JsonProperty(required = true)
        String display
    ) implements GroupUpdate {

    }
}
