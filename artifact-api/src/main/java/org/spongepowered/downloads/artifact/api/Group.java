package org.spongepowered.downloads.artifact.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.spongepowered.downloads.utils.UUIDType5;

import java.net.URL;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

@JsonDeserialize
public final class Group {

    private final String groupCoordinates;
    private final String name;
    private final String website;
    private final UUID groupId;

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

    public URL getWebsite() {
        return new URL(this.website);
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
