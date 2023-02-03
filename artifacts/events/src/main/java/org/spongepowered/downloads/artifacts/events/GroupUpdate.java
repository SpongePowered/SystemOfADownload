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
package org.spongepowered.downloads.artifacts.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

import java.io.Serial;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(GroupUpdate.GroupRegistered.class),
    @JsonSubTypes.Type(GroupUpdate.ArtifactRegistered.class),
})
public interface GroupUpdate {

    String groupId();

    @JsonTypeName("group-registered")
    @JsonDeserialize
    record GroupRegistered(String groupId, String name, String website)
        implements GroupUpdate {

        @JsonCreator
        public GroupRegistered {
        }

    }

    @JsonTypeName("artifact-registered")
    @JsonDeserialize
    final record ArtifactRegistered(ArtifactCoordinates coordinates) implements GroupUpdate {

        @Serial private static final long serialVersionUID = 6319289932327553919L;

        @JsonCreator
        public ArtifactRegistered {
        }


        @Override
        public String groupId() {
            return this.coordinates.groupId();
        }
    }

}
