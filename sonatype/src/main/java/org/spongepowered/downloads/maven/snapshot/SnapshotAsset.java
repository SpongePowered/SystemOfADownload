package org.spongepowered.downloads.maven.snapshot;

import java.util.Objects;

public final class SnapshotAsset {
    private final String classifier;
    private final String extension;
    private final String value;
    private final String updated;

    SnapshotAsset(final String classifier, final String extension, final String value, final String updated) {
        this.classifier = classifier;
        this.extension = extension;
        this.value = value;
        this.updated = updated;
    }

    public String classifier() {
        return this.classifier;
    }

    public String extension() {
        return this.extension;
    }

    public String value() {
        return this.value;
    }

    public String updated() {
        return this.updated;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        final var that = (SnapshotAsset) obj;
        return Objects.equals(this.classifier, that.classifier) &&
            Objects.equals(this.extension, that.extension) &&
            Objects.equals(this.value, that.value) &&
            Objects.equals(this.updated, that.updated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.classifier, this.extension, this.value, this.updated);
    }

    @Override
    public String toString() {
        return "SnapshotAsset[" +
            "classifier=" + this.classifier + ", " +
            "extension=" + this.extension + ", " +
            "value=" + this.value + ", " +
            "updated=" + this.updated + ']';
    }
}
