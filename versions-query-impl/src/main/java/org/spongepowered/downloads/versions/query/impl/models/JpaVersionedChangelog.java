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

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.spongepowered.downloads.versions.query.api.models.VersionedChangelog;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import java.io.Serializable;
import java.net.URL;
import java.util.Objects;

@Immutable
@Entity(name = "VersionedChangelog")
@Table(
    name = "versioned_changelogs",
    schema = "version"
)
@TypeDefs({
    @TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
})
public class JpaVersionedChangelog implements Serializable {

    @Id
    @Column(name = "version_id", updatable = false)
    private String versionId;

    @Id
    @Column(name = "group_id", updatable = false)
    private String groupId;

    @Id
    @Column(name = "artifact_id", updatable = false)
    private String artifactId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "version", referencedColumnName = "version", insertable = false, updatable = false),
        @JoinColumn(name = "group_id", referencedColumnName = "group_id", insertable = false, updatable = false),
        @JoinColumn(name = "artifact_id", referencedColumnName = "artifact_id", insertable = false, updatable = false)
    })
    private JpaVersionedArtifactView versionView;

    @Column(name = "commit_sha", nullable = false)
    private String sha;

    @Type(type = "jsonb")
    @Column(name = "changelog", columnDefinition = "jsonb")
    private VersionedChangelog changelog;

    @Column(name = "repo")
    private URL repo;

    @Column(name = "branch")
    private String branch;

    public String getSha() {
        return sha;
    }

    public VersionedChangelog getChangelog() {
        return changelog;
    }

    public URL getRepo() {
        return repo;
    }

    public String getBranch() {
        return branch;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JpaVersionedChangelog that = (JpaVersionedChangelog) o;
        return Objects.equals(versionId, that.versionId) && Objects.equals(
            groupId, that.groupId) && Objects.equals(artifactId, that.artifactId) && Objects.equals(
            versionView, that.versionView) && Objects.equals(sha, that.sha) && Objects.equals(
            changelog, that.changelog) && Objects.equals(repo, that.repo) && Objects.equals(
            branch, that.branch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(versionId, groupId, artifactId, versionView, sha, changelog, repo, branch);
    }

    @Override
    public String toString() {
        return "JpaVersionedChangelog{" +
            "versionId='" + versionId + '\'' +
            ", groupId='" + groupId + '\'' +
            ", artifactId='" + artifactId + '\'' +
            ", versionView=" + versionView +
            ", sha='" + sha + '\'' +
            ", changelog=" + changelog +
            ", repo=" + repo +
            ", branch='" + branch + '\'' +
            '}';
    }
}
