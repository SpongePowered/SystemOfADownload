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
package org.spongepowered.downloads.versions.api.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.vavr.collection.Map;
import org.spongepowered.downloads.versions.api.models.tags.ArtifactTagValue;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(
        value = GetVersionsResponse.VersionsAvailable.class,
        name = "Artifacts"
    ),
    @JsonSubTypes.Type(
        value = GetVersionsResponse.GroupUnknown.class,
        name = "UnknownGroup"),
    @JsonSubTypes.Type(
        value = GetVersionsResponse.ArtifactUnknown.class,
        name = "UnknownArtifact"
    )
})
public interface GetVersionsResponse {

    @JsonSerialize
    record VersionsAvailable(
        @JsonProperty Map<String, ArtifactTagValue> artifacts
    ) implements GetVersionsResponse {

    }

    @JsonSerialize
    record GroupUnknown(@JsonProperty String groupId) implements GetVersionsResponse {

    }

    @JsonSerialize
    record ArtifactUnknown(@JsonProperty String artifactId) implements GetVersionsResponse {
    }
}
