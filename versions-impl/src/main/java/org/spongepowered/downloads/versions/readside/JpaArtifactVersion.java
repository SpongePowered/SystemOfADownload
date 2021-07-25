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
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity(name = "ArtifactVersion")
@Table(name = "artifact_versions",
    schema = "version",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"artifact_id", "version"},
        name = "artifact_version_unique_idx")
)
class JpaArtifactVersion implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id",
        nullable = false)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artifact_id",
        foreignKey = @ForeignKey(name = "artifact_versions_artifact_id_fkey"),
        nullable = false)
    private JpaArtifact artifact;

    @OneToMany(
        mappedBy = "version",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        targetEntity = JpaVersionTagValue.class
    )
    private final Set<JpaVersionTagValue> tagValues = new HashSet<>();

    @Column(name = "version",
        nullable = false)
    private String version;

    void setArtifact(final JpaArtifact artifact) {
        this.artifact = artifact;
    }

    public JpaArtifact getArtifact() {
        return artifact;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(final String version) {
        this.version = version;
    }

    public Set<JpaVersionTagValue> getTagValues() {
        return this.tagValues;
    }

    public void addTagValue(JpaVersionTagValue versionTagValue) {
        this.tagValues.add(versionTagValue);
        versionTagValue.setVersion(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JpaArtifactVersion that = (JpaArtifactVersion) o;
        return id == that.id && Objects.equals(artifact, that.artifact) && Objects.equals(
            version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, artifact, version);
    }

}

