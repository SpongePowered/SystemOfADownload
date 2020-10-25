package org.spongepowered.downloads.artifact.api.query;

import java.net.URL;
import java.util.Objects;
import java.util.StringJoiner;

public final class RegisterGroupRequest {

    private final String groupName;
    private final String groupCoordinates;
    private final URL website;

    public RegisterGroupRequest(final String groupName, final String groupCoordinates, final URL website) {
        this.groupName = groupName;
        this.groupCoordinates = groupCoordinates;
        this.website = website;
    }

    public String getGroupName() {
        return this.groupName;
    }

    public String getGroupCoordinates() {
        return this.groupCoordinates;
    }

    public URL getWebsite() {
        return this.website;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final RegisterGroupRequest that = (RegisterGroupRequest) o;
        return Objects.equals(this.groupName, that.groupName) &&
            Objects.equals(this.groupCoordinates, that.groupCoordinates) &&
            Objects.equals(this.website, that.website);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.groupName, this.groupCoordinates, this.website);
    }

    @Override
    public String toString() {
        return new StringJoiner(
            ", ", RegisterGroupRequest.class.getSimpleName() + "[",
            "]"
        )
            .add("groupName='" + this.groupName + "'")
            .add("groupCoordinates='" + this.groupCoordinates + "'")
            .add("website=" + this.website)
            .toString();
    }
}
