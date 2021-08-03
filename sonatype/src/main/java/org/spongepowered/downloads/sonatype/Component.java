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
package org.spongepowered.downloads.sonatype;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.vavr.collection.List;

@JsonDeserialize
@JsonSerialize
public record Component(String id, String repository, String format,
                        String group, String name, String version,
                        List<Asset> assets) {
    @JsonCreator
    public Component(
        @JsonProperty(value = "id",
            required = true) final String id,
        @JsonProperty(value = "repository",
            required = true) final String repository,
        @JsonProperty(value = "format",
            required = true) final String format,
        @JsonProperty(value = "group",
            required = true) final String group,
        @JsonProperty(value = "name",
            required = true) final String name,
        @JsonProperty(value = "version",
            required = true) final String version,
        @JsonProperty(value = "assets",
            required = true) final List<Asset> assets
    ) {
        this.id = id;
        this.repository = repository;
        this.format = format;
        this.group = group;
        this.name = name;
        this.version = version;
        this.assets = assets;
    }

    @JsonDeserialize
    public static record Asset(
        @JsonProperty(value = "downloadUrl",
            required = true) String downloadUrl,
        @JsonProperty(value = "path",
            required = true) String path,
        @JsonProperty(value = "id",
            required = true) String id,
        @JsonProperty(value = "repository",
            required = true) String repository,
        @JsonProperty(value = "format",
            required = true) String format,
        @JsonProperty(value = "checksum",
            required = true) Checksum checksum,
        @JsonProperty(value = "contentType",
            required = true) String contentType,
        @JsonProperty(value = "lastModified",
            required = true) String lastModified,
        @JsonProperty(value = "maven2") Maven2 mavenData
    ) {
        @JsonCreator
        public Asset {
        }
    }

    public static record Maven2(
        @JsonProperty(value = "extension") String extension,
        @JsonProperty(value = "groupId") String groupId,
        @JsonProperty(value = "classifier") String classifier,
        @JsonProperty(value = "artifactId") String artifactId,
        @JsonProperty(value = "version") String version
    ) {

    }

    @JsonDeserialize
    public static record Checksum(
        @JsonProperty(value = "sha1") String sha1,
        @JsonProperty(value = "sha256") String sha256,
        @JsonProperty(value = "sha512") String sha512,
        @JsonProperty(value = "md5") String md5
    ) {
        @JsonCreator
        public Checksum {
        }

    }
}
