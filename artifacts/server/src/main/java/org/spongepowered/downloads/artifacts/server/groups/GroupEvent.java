/*
 * This file is part of SystemOfADownload, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://spongepowered.org/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.downloads.artifacts.server.groups;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

import java.io.Serial;
import java.util.Objects;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(GroupEvent.GroupRegistered.class),
    @JsonSubTypes.Type(GroupEvent.ArtifactRegistered.class),
})
public interface GroupEvent extends AggregateEvent<GroupEvent>, Jsonable {

    AggregateEventShards<GroupEvent> TAG = AggregateEventTag.sharded(GroupEvent.class, 10);

    @Override
    default AggregateEventTagger<GroupEvent> aggregateTag() {
        return TAG;
    }

    String groupId();

    @JsonTypeName("group-registered")
    @JsonDeserialize
    final class GroupRegistered implements GroupEvent {
        @Serial private static final long serialVersionUID = 0L;

        public final String groupId;
        public final String name;
        public final String website;

        @JsonCreator
        public GroupRegistered(final String groupId, final String name, final String website) {
            this.groupId = groupId;
            this.name = name;
            this.website = website;
        }

        @Override
        public String groupId() {
            return this.groupId;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final var that = (GroupRegistered) obj;
            return Objects.equals(this.groupId, that.groupId) &&
                Objects.equals(this.name, that.name) &&
                Objects.equals(this.website, that.website);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.groupId, this.name, this.website);
        }

        @Override
        public String toString() {
            return "GroupRegistered[" +
                "groupId=" + this.groupId + ", " +
                "name=" + this.name + ", " +
                "website=" + this.website + ']';
        }

    }

    @JsonTypeName("artifact-registered")
        @JsonDeserialize
    record ArtifactRegistered(
        String groupId,
        String artifact
    ) implements GroupEvent {

        public ArtifactCoordinates coordinates() {
            return new ArtifactCoordinates(this.groupId, this.artifact);
        }

        @JsonCreator
        public ArtifactRegistered {
        }
    }

}
