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
package org.spongepowered.downloads.versions.readside;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity(name = "Artifact")
@Table(name = "artifacts",
    schema = "version",
    indexes = {
        @Index(name = "grouped_artifact",
            columnList = "group_id, artifact_id",
            unique = true)
    })
@NamedQueries({
    @NamedQuery(
        name = "Artifact.selectByGroupAndArtifact",
        query = """
            select a from Artifact a where a.groupId = :groupId and a.artifactId = :artifactId
            """
    ),
    @NamedQuery(
        name = "Artifact.selectWithTags",
        query = """
            select a from Artifact a where a.groupId = :groupId and a.artifactId = :artifactId
            """
    )
})
public class JpaArtifact implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id",
        updatable = false,
        nullable = false)
    private int id;

    @Column(name = "group_id",
        nullable = false)
    private String groupId;

    @Column(name = "artifact_id",
        nullable = false)
    private String artifactId;

    @OneToMany(
        targetEntity = JpaArtifactTag.class,
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        mappedBy = "artifact")
    private Set<JpaArtifactTag> tags = new HashSet<>();

    @OneToMany(
        targetEntity = JpaArtifactVersion.class,
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        mappedBy = "artifact")
    private Set<JpaArtifactVersion> versions = new HashSet<>();

    @OneToOne(
        targetEntity = JpaArtifactRegexRecommendation.class,
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        mappedBy = "artifact"
    )
    private JpaArtifactRegexRecommendation regexRecommendation;

    public int getId() {
        return id;
    }

    public void setId(final int id) {
        this.id = id;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(final String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(final String artifactId) {
        this.artifactId = artifactId;
    }

    public Set<JpaArtifactTag> getTags() {
        return tags;
    }

    public void setTags(final Set<JpaArtifactTag> tags) {
        this.tags = tags;
    }

    public void addVersion(JpaArtifactVersion version) {
        this.versions.add(version);
        version.setArtifact(this);
    }

    public Set<JpaArtifactVersion> getVersions() {
        return versions;
    }

    public void setVersions(final Set<JpaArtifactVersion> versions) {
        this.versions = versions;
    }

    public void setRecommendation(
        final JpaArtifactRegexRecommendation regexRecommendation
    ) {
        this.regexRecommendation = regexRecommendation;
        regexRecommendation.setArtifact(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JpaArtifact that = (JpaArtifact) o;
        return id == that.id && Objects.equals(groupId, that.groupId) && Objects.equals(
            artifactId, that.artifactId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, groupId, artifactId);
    }

    public void addTag(JpaArtifactTag newTag) {
        this.tags.add(newTag);
        newTag.setArtifact(this);
    }
}
