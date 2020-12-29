package org.spongepowered.downloads.webhook;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Objects;

@JsonDeserialize
final class SonatypeComponent {
    private final String id;
    private final String componentId;
    private final String format;
    private final String name;
    private final String group;
    private final String version;

    SonatypeComponent(
        String id, String componentId, String format, String name, String group,
        String version
    ) {
        this.id = id;
        this.componentId = componentId;
        this.format = format;
        this.name = name;
        this.group = group;
        this.version = version;
    }

    public String id() {
        return this.id;
    }

    public String componentId() {
        return this.componentId;
    }

    public String format() {
        return this.format;
    }

    public String name() {
        return this.name;
    }

    public String group() {
        return this.group;
    }

    public String version() {
        return this.version;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (SonatypeComponent) obj;
        return Objects.equals(this.id, that.id) &&
            Objects.equals(this.componentId, that.componentId) &&
            Objects.equals(this.format, that.format) &&
            Objects.equals(this.name, that.name) &&
            Objects.equals(this.group, that.group) &&
            Objects.equals(this.version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.componentId, this.format, this.name, this.group, this.version);
    }

    @Override
    public String toString() {
        return "SonatypeComponent[" +
            "id=" + this.id + ", " +
            "componentId=" + this.componentId + ", " +
            "format=" + this.format + ", " +
            "name=" + this.name + ", " +
            "group=" + this.group + ", " +
            "version=" + this.version + ']';
    }

}
