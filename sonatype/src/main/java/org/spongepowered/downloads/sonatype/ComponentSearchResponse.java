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

import io.vavr.collection.List;

import java.util.Objects;
import java.util.Optional;

public final class ComponentSearchResponse {
    private final List<Item> items;
    private final Optional<String> continuationToken;

    public ComponentSearchResponse(final List<Item> items, final Optional<String> continuationToken) {
        this.items = items;
        this.continuationToken = continuationToken;
    }

    public List<Item> items() {
        return this.items;
    }

    public Optional<String> continuationToken() {
        return this.continuationToken;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        final var that = (ComponentSearchResponse) obj;
        return Objects.equals(this.items, that.items) &&
            Objects.equals(this.continuationToken, that.continuationToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.items, this.continuationToken);
    }

    @Override
    public String toString() {
        return "ComponentSearchResponse[" +
            "items=" + this.items + ", " +
            "continuationToken=" + this.continuationToken + ']';
    }

    public final static class Item {
        private final String id;
        private final String repository;
        private final String format;
        private final String group;
        private final String name;
        private final String version;

        public Item(final String id, final String repository, final String format, final String group, final String name, final String version) {
            this.id = id;
            this.repository = repository;
            this.format = format;
            this.group = group;
            this.name = name;
            this.version = version;
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

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (Item) obj;
            return Objects.equals(this.id, that.id) &&
                Objects.equals(this.repository, that.repository) &&
                Objects.equals(this.format, that.format) &&
                Objects.equals(this.group, that.group) &&
                Objects.equals(this.name, that.name) &&
                Objects.equals(this.version, that.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.id, this.repository, this.format, this.group, this.name, this.version);
        }

        @Override
        public String toString() {
            return "Item[" +
                "id=" + this.id + ", " +
                "repository=" + this.repository + ", " +
                "format=" + this.format + ", " +
                "group=" + this.group + ", " +
                "name=" + this.name + ", " +
                "version=" + this.version + ']';
        }
    }
}
