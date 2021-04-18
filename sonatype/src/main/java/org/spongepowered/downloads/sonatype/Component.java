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

import java.util.Objects;

@JsonDeserialize
@JsonSerialize
public final class Component {
    private final String id;
    private final String repository;
    private final String format;
    private final String group;
    private final String name;
    private final String version;
    private final List<Asset> assets;

    @JsonCreator
    public Component(
        @JsonProperty(value = "id", required = true) final String id,
        @JsonProperty(value = "repository", required = true) final String repository,
        @JsonProperty(value = "format", required = true) final String format,
        @JsonProperty(value = "group", required = true) final String group,
        @JsonProperty(value = "name", required = true) final String name,
        @JsonProperty(value = "version", required = true) final String version,
        @JsonProperty(value = "assets", required = true) final List<Asset> assets
    ) {
        this.id = id;
        this.repository = repository;
        this.format = format;
        this.group = group;
        this.name = name;
        this.version = version;
        this.assets = assets;
    }

    public String id() {
        return this.id;
    }

    public String repository() {
        return this.repository;
    }

    public String format() {
        return this.format;
    }

    public String group() {
        return this.group;
    }

    public String name() {
        return this.name;
    }

    public String version() {
        return this.version;
    }

    public List<Asset> assets() {
        return this.assets;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        final var that = (Component) obj;
        return Objects.equals(this.id, that.id) &&
            Objects.equals(this.repository, that.repository) &&
            Objects.equals(this.format, that.format) &&
            Objects.equals(this.group, that.group) &&
            Objects.equals(this.name, that.name) &&
            Objects.equals(this.version, that.version) &&
            Objects.equals(this.assets, that.assets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.repository, this.format, this.group, this.name, this.version, this.assets);
    }

    @Override
    public String toString() {
        return "Component[" +
            "id=" + this.id + ", " +
            "repository=" + this.repository + ", " +
            "format=" + this.format + ", " +
            "group=" + this.group + ", " +
            "name=" + this.name + ", " +
            "version=" + this.version + ", " +
            "assets=" + this.assets + ']';
    }


    @JsonDeserialize
    public static final class Asset {
        private final String downloadUrl;
        private final String path;
        private final String id;
        private final String repository;
        private final String format;
        private final Checksum checksum;

        @JsonCreator
        public Asset(
            @JsonProperty(value = "downloadUrl") final String downloadUrl,
            @JsonProperty(value = "path") final String path,
            @JsonProperty(value = "id") final String id,
            @JsonProperty(value = "repository") final String repository,
            @JsonProperty(value = "format") final String format,
            @JsonProperty(value = "checksum") final Checksum checksum
        ) {
            this.downloadUrl = downloadUrl;
            this.path = path;
            this.id = id;
            this.repository = repository;
            this.format = format;
            this.checksum = checksum;
        }

        public String downloadUrl() {
            return this.downloadUrl;
        }

        public String path() {
            return this.path;
        }

        public String id() {
            return this.id;
        }

        public String repository() {
            return this.repository;
        }

        public String format() {
            return this.format;
        }

        public Checksum checksum() {
            return this.checksum;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final var that = (Asset) obj;
            return Objects.equals(this.downloadUrl, that.downloadUrl) &&
                Objects.equals(this.path, that.path) &&
                Objects.equals(this.id, that.id) &&
                Objects.equals(this.repository, that.repository) &&
                Objects.equals(this.format, that.format) &&
                Objects.equals(this.checksum, that.checksum);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.downloadUrl, this.path, this.id, this.repository, this.format, this.checksum);
        }

        @Override
        public String toString() {
            return "Asset[" +
                "downloadUrl=" + this.downloadUrl + ", " +
                "path=" + this.path + ", " +
                "id=" + this.id + ", " +
                "repository=" + this.repository + ", " +
                "format=" + this.format + ", " +
                "checksum=" + this.checksum + ']';
        }
    }

    @JsonDeserialize
    public static final class Checksum {
        private final String sha1;
        public final String sha256;
        public final String sha512;
        private final String md5;

        @JsonCreator
        public Checksum(
            @JsonProperty(value = "sha1") final String sha1,
            @JsonProperty(value = "sha256") final String sha256,
            @JsonProperty(value = "sha512") final String sha512,
            @JsonProperty(value = "md5") final String md5
        ) {
            this.sha1 = sha1;
            this.sha256 = sha256;
            this.sha512 = sha512;
            this.md5 = md5;
        }

        public String sha1() {
            return this.sha1;
        }

        public String md5() {
            return this.md5;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final var that = (Checksum) obj;
            return Objects.equals(this.sha1, that.sha1) &&
                Objects.equals(this.md5, that.md5);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.sha1, this.md5);
        }

        @Override
        public String toString() {
            return "Checksum[" +
                "sha1=" + this.sha1 + ", " +
                "md5=" + this.md5 + ']';
        }
    }
}
