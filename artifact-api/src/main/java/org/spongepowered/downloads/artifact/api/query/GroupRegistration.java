package org.spongepowered.downloads.artifact.api.query;

import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.Group;

import java.io.Serial;
import java.net.URL;
import java.util.Objects;

public final class GroupRegistration {

    public static final class RegisterGroupRequest {
        private final String groupName;
        private final String groupCoordinates;
        private final String website;

        public RegisterGroupRequest(
            String groupName,
            String groupCoordinates,
            String website
        ) {
            this.groupName = groupName;
            this.groupCoordinates = groupCoordinates;
            this.website = website;
        }

        public String groupName() {
            return this.groupName;
        }

        public String groupCoordinates() {
            return this.groupCoordinates;
        }

        public String website() {
            return this.website;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (RegisterGroupRequest) obj;
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
            return "RegisterGroupRequest[" +
                "groupName=" + this.groupName + ", " +
                "groupCoordinates=" + this.groupCoordinates + ", " +
                "website=" + this.website + ']';
        }
    }

    public interface Response extends Jsonable {

        final static class GroupAlreadyRegistered implements Response {
            @Serial private static final long serialVersionUID = 0L;
            private final String groupNameRequested;

            public GroupAlreadyRegistered(String groupNameRequested) {
                this.groupNameRequested = groupNameRequested;
            }

            public String groupNameRequested() {
                return this.groupNameRequested;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                var that = (GroupAlreadyRegistered) obj;
                return Objects.equals(this.groupNameRequested, that.groupNameRequested);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.groupNameRequested);
            }

            @Override
            public String toString() {
                return "GroupAlreadyRegistered[" +
                    "groupNameRequested=" + this.groupNameRequested + ']';
            }
        }

        final static class GroupRegistered implements Response {
            @Serial private static final long serialVersionUID = 0L;
            private final Group group;

            public GroupRegistered(Group group) {
                this.group = group;
            }

            public Group group() {
                return this.group;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                var that = (GroupRegistered) obj;
                return Objects.equals(this.group, that.group);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.group);
            }

            @Override
            public String toString() {
                return "GroupRegistered[" +
                    "group=" + this.group + ']';
            }
        }
    }
}
