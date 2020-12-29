package org.spongepowered.downloads.webhook.sonatype;

import java.util.Objects;

public final class MavenPom {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String name;

    public MavenPom(final String groupId, final String artifactId, final String version, final String name) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.name = name;
    }

    public String groupId() {
        return this.groupId;
    }

    public String artifactId() {
        return this.artifactId;
    }

    public String version() {
        return this.version;
    }

    public String name() {
        return this.name;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        final var that = (MavenPom) obj;
        return Objects.equals(this.groupId, that.groupId) &&
            Objects.equals(this.artifactId, that.artifactId) &&
            Objects.equals(this.version, that.version) &&
            Objects.equals(this.name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.groupId, this.artifactId, this.version, this.name);
    }

    @Override
    public String toString() {
        return "MavenPom[" +
            "groupId=" + this.groupId + ", " +
            "artifactId=" + this.artifactId + ", " +
            "version=" + this.version + ", " +
            "name=" + this.name + ']';
    }
}
