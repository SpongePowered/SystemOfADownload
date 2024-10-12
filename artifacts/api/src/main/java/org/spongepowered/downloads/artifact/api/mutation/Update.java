package org.spongepowered.downloads.artifact.api.mutation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
    property = "type")
@JsonDeserialize
public sealed interface Update {
    // This is a relatively simple regex that should cover most validation cases
    // It is not perfect and may not cover all edge cases, but realistically
    // we're only checking for basic URL format to link to an issues page or
    // something similar.
    String URL_REGEX = "^(https?):\\/\\/(www\\.)?([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,6}(:[0-9]{1,5})?(/[a-zA-Z0-9-._~:/?#/@!$&'()*+,;=%]*)?$";
    // And this is yet a different git url validation regex, only to verify
    // since we need to be able to query git repositories.
    String GIT_URL_PATTERN = "^(https://|git://|git@)([a-zA-Z0-9.-]+)[:/]([a-zA-Z0-9._-]+)/([a-zA-Z0-9._-]+)(\\.git)$";

    @JsonTypeName("website")
    @Introspected
    record Website(
        @Pattern(regexp = URL_REGEX,
            message = "Invalid URL format")
        @JsonProperty(required = true)
        String website
    ) implements Update {

    }

    @JsonTypeName("displayName")
    @Introspected
    record DisplayName(
        @NotBlank
        @Size(min = 1, max = 255)
        @JsonProperty(required = true)
        String display
    ) implements Update {

    }

    @JsonTypeName("issues")
    @Introspected
    record Issues(
        @Pattern(regexp = URL_REGEX,
            message = "Invalid URL format")
        @JsonProperty(required = true) String issues
    ) implements Update {

    }

    @JsonTypeName("git-repo")
    @Introspected
    record GitRepository(
        @Pattern(regexp = GIT_URL_PATTERN,
            message = "Invalid URL format")
        @JsonProperty(required = true) String gitRepo
    ) implements Update {

    }

    @JsonTypeName("git-repos")
    @Introspected
    record GitRepositories(
        @Pattern(regexp = GIT_URL_PATTERN,
            message = "Invalid URL format")
        @JsonProperty(required = true)
        List<String> gitRepos
    ) implements Update {

    }
}
