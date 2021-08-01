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
package org.spongepowered.downloads.artifacts.query.impl.model;

import org.hibernate.annotations.Immutable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

@Immutable
@Entity(name = "ArtifactTagValue")
@Table(name = "artifact_tag_values",
    schema = "version")
@IdClass(JpaArtifactTagValue.Identifier.class)
public class JpaArtifactTagValue {

    /*
    This identifier is required to list basically the columns all available
    because JPA will consider the specific id's as "unique", but since this is
    not a unique columnar list, we treat all the columns as '@Id'. In this way,
    we're able to gather all the unique tag values from the artifact by the
    abused JoinColumns.
     */
    static final class Identifier implements Serializable {
        long id;
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
            return id == that.id && Objects.equals(artifactId, that.artifactId) && Objects.equals(
                groupId, that.groupId) && Objects.equals(tagName, that.tagName) && Objects.equals(
                tagValue, that.tagValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, artifactId, groupId, tagName, tagValue);
        }
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "id",
            referencedColumnName = "id"),
        @JoinColumn(name = "artifact_id",
            referencedColumnName = "artifact_id"),
        @JoinColumn(name = "group_id",
            referencedColumnName = "group_id")
    })
    private JpaArtifact artifact;

    @Id
    @Column(name = "artifact_id",
        insertable = false,
        updatable = false)
    private String artifactId;
    @Id
    @Column(name = "group_id",
        insertable = false,
        updatable = false)
    private String groupId;
    @Id
    @Column(name = "id",
        insertable = false,
        updatable = false)
    private long id;

    @Id
    @Column(name = "tag_name",
        insertable = false,
        updatable = false)
    private String tagName;

    @Id
    @Column(name = "tag_value",
        insertable = false,
        updatable = false)
    private String tagValue;

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
