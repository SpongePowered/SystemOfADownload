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
package org.spongepowered.downloads.versions.models;

import org.hibernate.annotations.Immutable;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

@Entity(name = "TaggedVersion")
@Immutable
@Table(name = "versioned_tags", schema = "version")
@NamedQueries({
    @NamedQuery(name = "TaggedVersion.findAllForVersion",
        query =
            """
            select view from TaggedVersion view
            where view.mavenGroupId = :groupId and view.mavenArtifactId = :artifactId and view.version = :version
            """
    ),
    @NamedQuery(
        name = "TaggedVersion.findAllMatchingTagValues",
        query =
            """
            select view from TaggedVersion view
            where view.mavenGroupId = :groupId
            and view.mavenArtifactId = :artifactId
            and view.tagName = :tagName
            and view.tagValue like :tagValue
            """
    ),
    @NamedQuery(
        name = "TaggedVersion.findMatchingTagValuesAndRecommendation",
        query =
            """
            select view from TaggedVersion view
            where view.mavenGroupId = :groupId
            and view.mavenArtifactId = :artifactId
            and view.tagName = :tagName
            and view.tagValue like :tagValue
            and (view.versionView.recommended = :recommended or view.versionView.manuallyRecommended = :recommended)
            """
    )
})
public class JpaTaggedVersion implements Serializable {

    @Id
    @Column(name = "version_id", updatable = false)
    private long versionId;

    @Id
    @Column(updatable = false, name = "artifact_internal_id")
    private long artifactId;

    @Id
    @Column(name = "maven_group_id", updatable = false)
    private String mavenGroupId;

    @Id
    @Column(name = "maven_artifact_id", updatable = false)
    private String mavenArtifactId;

    @Id
    @Column(name = "maven_version",
        updatable = false)
    private String version;

    @Id
    @Column(name = "tag_name",
        updatable = false)
    private String tagName;

    @Column(name = "tag_value",
        updatable = false)
    private String tagValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "maven_version",
            referencedColumnName = "version",
            nullable = false,
            updatable = false,
            insertable = false),
        @JoinColumn(name = "maven_group_id",
            referencedColumnName = "group_id",
            nullable = false,
            updatable = false,
            insertable = false),
        @JoinColumn(name = "maven_artifact_id",
            referencedColumnName = "artifact_id",
            nullable = false,
            updatable = false,
            insertable = false)
    })
    private JpaVersionedArtifactView versionView;

    public String getTagName() {
        return tagName;
    }

    public String getTagValue() {
        return tagValue;
    }

    public MavenCoordinates asMavenCoordinates() {
        return new MavenCoordinates(this.mavenGroupId, this.mavenArtifactId, this.version);
    }

    public JpaVersionedArtifactView getVersion() {
        return this.versionView;
    }

    public String getMavenVersion() {
        return this.version;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JpaTaggedVersion that = (JpaTaggedVersion) o;
        return versionId == that.versionId && artifactId == that.artifactId && Objects.equals(
            mavenGroupId, that.mavenGroupId) && Objects.equals(
            mavenArtifactId, that.mavenArtifactId) && Objects.equals(
            version, that.version) && Objects.equals(tagName, that.tagName) && Objects.equals(
            tagValue, that.tagValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(versionId, artifactId, mavenGroupId, mavenArtifactId, version, tagName, tagValue);
    }
}
