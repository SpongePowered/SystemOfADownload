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
package org.spongepowered.downloads.artifact.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import org.spongepowered.downloads.utils.UUIDType5;

import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

@JsonDeserialize
public final class Group {

    @Schema(required = true)
    @JsonProperty(required = true)
    public final String groupCoordinates;
    @Schema(required = true)
    @JsonProperty(required = true)
    public final String name;
    @Schema(required = true)
    @JsonProperty(required = true)
    public final String website;
    @JsonIgnore
    public final UUID groupId;

    @JsonCreator
    public Group(final String groupCoordinates, final String name, final String website) {
        this.groupCoordinates = groupCoordinates;
        this.name = name;
        this.website = website;
        this.groupId = UUIDType5.nameUUIDFromNamespaceAndString(UUIDType5.NAMESPACE_OID, groupCoordinates);
    }

    public String getGroupCoordinates() {
        return this.groupCoordinates;
    }

    public String getName() {
        return this.name;
    }

    public String getWebsite() {
        return this.website;
    }

    public UUID getGroupId() {
        return this.groupId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final Group group = (Group) o;
        return Objects.equals(this.groupCoordinates, group.groupCoordinates) &&
            Objects.equals(this.name, group.name) &&
            Objects.equals(this.website, group.website);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.groupCoordinates, this.name, this.website);
    }

    @Override
    public String
    toString() {
        return new StringJoiner(
            ", ", Group.class.getSimpleName() + "[", "]")
            .add("groupCoordinates='" + this.groupCoordinates + "'")
            .add("name='" + this.name + "'")
            .add("website=" + this.website)
            .toString();
    }
}
