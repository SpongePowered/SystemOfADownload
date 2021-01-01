package org.spongepowered.downloads.artifact.api.query;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.spongepowered.downloads.artifact.api.Group;

import java.util.Objects;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
    @JsonSubTypes.Type(value = GroupResponse.Missing.class, name = "MissingGroup"),
    @JsonSubTypes.Type(value = GroupResponse.Available.class, name = "Group")
})
public interface GroupResponse {

    @JsonSerialize
    final static class Missing implements GroupResponse {
        @JsonProperty
        public final String groupId;

        public Missing(final String groupId) {
            this.groupId = groupId;
        }

        public String groupId() {
            return this.groupId;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (Missing) obj;
            return Objects.equals(this.groupId, that.groupId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.groupId);
        }

        @Override
        public String toString() {
            return "Missing[" +
                "groupId=" + this.groupId + ']';
        }
    }
    @JsonSerialize
    final static class Available implements GroupResponse {
        @JsonProperty
        public final Group group;

        public Available(final Group group) {
            this.group = group;
        }

        public Group group() {
            return this.group;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (Available) obj;
            return Objects.equals(this.group, that.group);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.group);
        }

        @Override
        public String toString() {
            return "Available[" +
                "group=" + this.group + ']';
        }
    }

}
