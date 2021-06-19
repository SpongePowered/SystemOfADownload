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
package org.spongepowered.downloads.maven.artifact;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.vavr.collection.List;

import java.util.Objects;
import java.util.Optional;

@JsonDeserialize
public final class Versioning {
    public final String latest;
    public final String release;
    public final String lastUpdated;
    public final List<String> versions;

    public Versioning() {
        this.latest = "";
        this.release = "";
        this.lastUpdated = "";
        this.versions = List.empty();
    }

    @JsonCreator
    public Versioning(
        @JsonProperty("latest") final String latest,
        @JsonProperty("release") final String release,
        @JsonProperty("lastUpdated") String lastUpdated,
        @JsonProperty("versions") List<String> versions
    ) {
        this.latest = latest == null ? "" : latest;
        this.release = release == null ? "" : release;
        this.lastUpdated = lastUpdated;
        this.versions = versions;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        final var that = (Versioning) obj;
        return Objects.equals(this.latest, that.latest) &&
            Objects.equals(this.release, that.release) &&
            Objects.equals(this.lastUpdated, that.lastUpdated) &&
            Objects.equals(this.versions, that.versions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.latest, this.release, this.lastUpdated, this.versions);
    }

    @Override
    public String toString() {
        return "Versioning[" +
            "latest=" + this.latest + ", " +
            "release=" + this.release + ", " +
            "lastUpdated=" + this.lastUpdated + ", " +
            "versions=" + this.versions+ ']';
    }
}
