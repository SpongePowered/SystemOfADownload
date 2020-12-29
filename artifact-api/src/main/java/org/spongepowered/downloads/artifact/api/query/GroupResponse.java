package org.spongepowered.downloads.artifact.api.query;

import org.spongepowered.downloads.artifact.api.Group;

import java.util.Objects;

public interface GroupResponse {

    final static class Missing implements GroupResponse {
        private final String groupId;

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

    final static class Available implements GroupResponse {
        private final Group group;

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
