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
package org.spongepowered.downloads.artifacts.server.query.meta.domain;

import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;

import java.io.Serializable;
import java.util.Objects;

@MappedEntity(value = "versioned_tags", schema = "version")
public class JpaArtifactTagValue {

    /*
    This identifier is required to list basically the columns all available
    because JPA will consider the specific id's as "unique", but since this is
    not a unique columnar list, we treat all the columns as '@Id'. In this way,
    we're able to gather all the unique tag values from the artifact by the
    abused JoinColumns.
     */
    static final class Identifier implements Serializable {
        String artifactId;
        String groupId;
        String tagName;
        String tagValue;

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Identifier that = (Identifier) o;
            return Objects.equals(artifactId, that.artifactId) && Objects.equals(
                groupId, that.groupId) && Objects.equals(tagName, that.tagName) && Objects.equals(
                tagValue, that.tagValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(artifactId, groupId, tagName, tagValue);
        }
    }

    private JpaArtifact artifact;

    @MappedProperty(value = "artifact_id")
    private String artifactId;
    @MappedProperty(value = "group_id")
    private String groupId;

    @MappedProperty(value = "tag_name")
    private String tagName;

    @MappedProperty(value = "tag_value")
    private String tagValue;

    public JpaArtifact getArtifact() {
        return artifact;
    }

    public String getTagName() {
        return tagName;
    }

    public String getTagValue() {
        return tagValue;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JpaArtifactTagValue that = (JpaArtifactTagValue) o;
        return artifact.equals(that.artifact) && tagName.equals(that.tagName) && tagValue.equals(that.tagValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifact, tagName, tagValue);
    }
}
