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
package org.spongepowered.downloads.git.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;

@JsonDeserialize
@Immutable
public final class Repository {

    private final UUID id;
    private final String name;
    private final String repoUrl;
    private final @Nullable String website;

    public Repository(
        final UUID id, final String name, final String repoUrl, @Nullable final String website
    ) {
        this.id = id;
        this.name = name;
        this.repoUrl = repoUrl;
        this.website = website;
    }

    private Repository(final Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.repoUrl = builder.repoUrl;
        this.website = builder.website;
    }

    public UUID getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getRepoUrl() {
        return this.repoUrl;
    }

    public Optional<String> getWebsite() {
        return Optional.ofNullable(this.website);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final Repository that = (Repository) o;
        return this.id.equals(that.id) &&
            this.name.equals(that.name) &&
            this.repoUrl.equals(that.repoUrl) &&
            Objects.equals(this.website, that.website);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.name, this.repoUrl, this.website);
    }

    @Override
    public String toString() {
        return new StringJoiner(
            ", ", Repository.class.getSimpleName() + "[", "]")
            .add("id=" + this.id)
            .add("name='" + this.name + "'")
            .add("repoUrl='" + this.repoUrl + "'")
            .add("website='" + this.website + "'")
            .toString();
    }

    public static final class Builder {
        private UUID id;
        private String name;
        private String repoUrl;
        private @Nullable String website;

        public Builder() {}

        public Builder setId(final UUID id) {
            this.id = id;
            return this;
        }

        public Builder setName(final String name) {
            this.name = name;
            return this;
        }

        public Builder setRepoUrl(final String repoUrl) {
            this.repoUrl = repoUrl;
            return this;
        }

        public Builder setWebsite(final String website) {
            this.website = website;
            return this;
        }

        public Repository build() {
            Objects.requireNonNull(this.id, "Repositories must have an id");
            Objects.requireNonNull(this.name, "Repositories must have a name");
            Objects.requireNonNull(this.repoUrl, "Repository URL must be set");
            return new Repository(this);
        }
    }
}
