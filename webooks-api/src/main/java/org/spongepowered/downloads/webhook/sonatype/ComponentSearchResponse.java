package org.spongepowered.downloads.webhook.sonatype;

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
