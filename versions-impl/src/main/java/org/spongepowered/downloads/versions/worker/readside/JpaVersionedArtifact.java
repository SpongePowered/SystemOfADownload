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
package org.spongepowered.downloads.versions.worker.readside;

import org.spongepowered.downloads.versions.server.readside.JpaArtifact;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.io.Serializable;
import java.util.Objects;

@Entity(name = "GitVersionedArtifact")
@Table(name = "artifact_versions",
    schema = "version",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"artifact_id", "version"},
        name = "artifact_version_unique_idx")
)
@NamedQueries({
    @NamedQuery(
        name = "GitVersionedArtifact.findByCoordinates",
        query =
            """
            select distinct v from GitVersionedArtifact v
            where v.artifact.groupId = :groupId and v.artifact.artifactId = :artifactId and v.version = :version
            """
    )
})
public class JpaVersionedArtifact implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id",
        updatable = false,
        nullable = false)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artifact_id",
        foreignKey = @ForeignKey(name = "artifact_versions_artifact_id_fkey"),
        nullable = false)
    private JpaArtifact artifact;

    @Column(name = "version",
        nullable = false)
    private String version;

    @OneToOne(targetEntity = JpaVersionChangelog.class, fetch = FetchType.LAZY)
    @JoinColumn(name = "id", referencedColumnName = "version_id")
    private JpaVersionChangelog changelog;

    public JpaArtifact getArtifact() {
        return artifact;
    }

    public String getVersion() {
        return version;
    }

    public JpaVersionChangelog getChangelog() {
        return changelog;
    }

    public void setChangelog(final JpaVersionChangelog changelog) {
        this.changelog = changelog;
        changelog.setId(this.id);
        changelog.setArtifact(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JpaVersionedArtifact that = (JpaVersionedArtifact) o;
        return id == that.id && Objects.equals(artifact, that.artifact) && Objects.equals(
            version, that.version) && Objects.equals(changelog, that.changelog);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, artifact, version, changelog);
    }
}
