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
package org.spongepowered.downloads.versions.query.impl.models;

import org.hibernate.annotations.Immutable;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

@Immutable
@Entity(name = "VersionedArtifactView")
@Table(name = "versioned_artifacts",
    schema = "version")
@NamedQueries({
    @NamedQuery(
        name = "VersionedArtifactView.count",
        query = """
                select count(v) from VersionedArtifactView v
                where v.groupId = :groupId and v.artifactId = :artifactId
                 """
    ),
    @NamedQuery(
        name = "VersionedArtifactView.findByArtifact",
        query = """
                select v from VersionedArtifactView v where v.artifactId = :artifactId and v.groupId = :groupId
                """
    )
})
public class JpaVersionedArtifactView implements Serializable {

    @Id
    @Column(name = "artifact_id",
        updatable = false)
    private String artifactId;

    @Id
    @Column(name = "group_id",
        updatable = false)
    private String groupId;

    @Id
    @Column(name = "version",
        updatable = false)
    private String version;

    public MavenCoordinates toMavenCoordinates() {
        return new MavenCoordinates(this.groupId, this.artifactId, this.version);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JpaVersionedArtifactView that = (JpaVersionedArtifactView) o;
        return Objects.equals(artifactId, that.artifactId) && Objects.equals(
            groupId, that.groupId) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifactId, groupId, version);
    }
}
