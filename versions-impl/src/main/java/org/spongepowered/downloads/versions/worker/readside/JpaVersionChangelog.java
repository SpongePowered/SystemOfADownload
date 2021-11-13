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

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.spongepowered.downloads.versions.api.models.VersionedChangelog;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.MapsId;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import java.net.URL;
import java.util.Objects;

@Entity(name = "VersionedChangelog")
@Table(name = "version_changelogs", schema = "version")
@TypeDefs({
    @TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
})
public class JpaVersionChangelog {

    @Id
    @Column(name = "version_id")
    private long id;

    @OneToOne(mappedBy = "changelog", optional = false)
    @MapsId("id")
    private JpaVersionedArtifact artifact;

    @Column(name = "commit_sha", nullable = false)
    private String sha;

    @Type(type = "jsonb")
    @Column(name = "changelog", columnDefinition = "jsonb")
    private VersionedChangelog changelog;

    @Column(name = "repo")
    private URL repo;

    @Column(name = "branch")
    private String branch;

    public long getId() {
        return id;
    }

    void setId(long id) {
        this.id = id;
    }

    public void setSha(final String sha) {
        this.sha = sha;
    }

    public void setRepo(final URL repo) {
        this.repo = repo;
    }

    public void setBranch(final String branch) {
        this.branch = branch;
    }

    public JpaVersionedArtifact getArtifact() {
        return artifact;
    }

    void setArtifact(JpaVersionedArtifact artifact) {
        this.artifact = artifact;
    }

    public void setChangelog(VersionedChangelog changelog) {
        this.changelog = changelog;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JpaVersionChangelog that = (JpaVersionChangelog) o;
        return id == that.id && Objects.equals(artifact, that.artifact) && Objects.equals(
            sha, that.sha) && Objects.equals(changelog, that.changelog) && Objects.equals(
            repo, that.repo) && Objects.equals(branch, that.branch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, artifact, sha, changelog, repo, branch);
    }

    @Override
    public String toString() {
        return "JpaVersionChangelog{" +
            "id=" + id +
            ", artifact=" + artifact +
            ", sha='" + sha + '\'' +
            ", changelog=" + changelog +
            ", repo=" + repo +
            ", branch='" + branch + '\'' +
            '}';
    }
}
