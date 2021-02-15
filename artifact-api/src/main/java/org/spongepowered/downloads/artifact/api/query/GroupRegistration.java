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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.Group;

import java.io.Serial;
import java.util.Objects;

public final class GroupRegistration {

    @JsonDeserialize
    public static final class RegisterGroupRequest {

        /**
         * The name of the group, displayed for reading purposes
         */
        @JsonProperty(required = true)
        public final String name;
        /**
         * The maven group coordinates of the group.
         */
        @JsonProperty(required = true)
        public final String groupCoordinates;
        /**
         * A website for the group
         */
        @JsonProperty(required = true)
        public final String website;

        @JsonCreator
        public RegisterGroupRequest(
            final String name,
            final String groupCoordinates,
            final String website
        ) {
            this.name = name;
            this.groupCoordinates = groupCoordinates;
            this.website = website;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (RegisterGroupRequest) obj;
            return Objects.equals(this.name, that.name) &&
                Objects.equals(this.groupCoordinates, that.groupCoordinates) &&
                Objects.equals(this.website, that.website);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.name, this.groupCoordinates, this.website);
        }

        @Override
        public String toString() {
            return "RegisterGroupRequest[" +
                "name=" + this.name + ", " +
                "groupCoordinates=" + this.groupCoordinates + ", " +
                "website=" + this.website + ']';
        }
    }

    public interface Response extends Jsonable {

        final class GroupAlreadyRegistered implements Response {
            @Serial private static final long serialVersionUID = 0L;
            private final String groupNameRequested;

            public GroupAlreadyRegistered(final String groupNameRequested) {
                this.groupNameRequested = groupNameRequested;
            }

            public String groupNameRequested() {
                return this.groupNameRequested;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (GroupAlreadyRegistered) obj;
                return Objects.equals(this.groupNameRequested, that.groupNameRequested);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.groupNameRequested);
            }

            @Override
            public String toString() {
                return "GroupAlreadyRegistered[" +
                    "groupNameRequested=" + this.groupNameRequested + ']';
            }
        }

        final class GroupRegistered implements Response {
            @Serial private static final long serialVersionUID = 0L;
            private final Group group;

            public GroupRegistered(final Group group) {
                this.group = group;
            }

            public Group group() {
                return this.group;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (GroupRegistered) obj;
                return Objects.equals(this.group, that.group);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.group);
            }

            @Override
            public String toString() {
                return "GroupRegistered[" +
                    "group=" + this.group + ']';
            }
        }
    }
}
