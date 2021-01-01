package org.spongepowered.downloads.artifact.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import org.spongepowered.downloads.utils.UUIDType5;

import javax.annotation.concurrent.Immutable;
import java.net.URL;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

@Immutable
@JsonDeserialize
public final class Group {

    @Schema(required = true)
    @JsonProperty
    public final String groupCoordinates;
    @Schema(required = true)
    @JsonProperty
    public final String name;
    @Schema(required = true)
    @JsonProperty
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
