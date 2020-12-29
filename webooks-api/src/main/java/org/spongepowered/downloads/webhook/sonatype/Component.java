package org.spongepowered.downloads.webhook.sonatype;

import io.vavr.collection.List;

import java.util.Objects;

public final class Component {
    private final String id;
    private final String repository;
    private final String format;
    private final String group;
    private final String name;
    private final String version;
    private final List<Asset> assets;

    public Component(

        final String id,
        final String repository,
        final String format,
        final String group,
        final String name,
        final String version,
        final List<Asset> assets
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
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
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


    public static final class Asset {
        private final String downloadUrl;
        private final String path;
        private final String id;
        private final String repository;
        private final String format;
        private final Checksum checksum;

        public Asset(
            final String downloadUrl,
            final String path,
            final String id,
            final String repository,
            final String format,
            final Checksum checksum
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
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
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

    public static final class Checksum {
        private final String sha1;
        private final String md5;

        public Checksum(
            final String sha1,
            final String md5
        ) {
            this.sha1 = sha1;
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
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
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
