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
package org.spongepowered.downloads.artifact.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.spongepowered.downloads.artifact.api.Group;

import java.util.Objects;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = GroupResponse.Missing.class, name = "MissingGroup"),
    @JsonSubTypes.Type(value = GroupResponse.Available.class, name = "Group")
})
public interface GroupResponse {

    @JsonSerialize
    final class Missing implements GroupResponse {
        @JsonProperty
        public final String groupId;

        @JsonCreator
        public Missing(final String groupId) {
            this.groupId = groupId;
        }

        public String groupId() {
            return this.groupId;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (Missing) obj;
            return Objects.equals(this.groupId, that.groupId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.groupId);
        }

        @Override
        public String toString() {
            return "Missing[" +
                "groupId=" + this.groupId + ']';
        }
    }
    @JsonSerialize
    final class Available implements GroupResponse {

        @JsonProperty
        public final Group group;

        @JsonCreator
        public Available(final Group group) {
            this.group = group;
        }

        public Group group() {
            return this.group;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (Available) obj;
            return Objects.equals(this.group, that.group);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.group);
        }

        @Override
        public String toString() {
            return "Available[" +
                "group=" + this.group + ']';
        }
    }

}
