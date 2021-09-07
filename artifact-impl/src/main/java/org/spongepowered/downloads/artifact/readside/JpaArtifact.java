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
package org.spongepowered.downloads.artifact.readside;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import java.util.Objects;

@Entity(name = "Artifact")
@Table(name = "artifacts",
    schema = "version")
@NamedQueries({
    @NamedQuery(name = "Artifact.findById",
        query = "select a from Artifact a where a.groupId = :groupId and a.artifactId = :artifactId"
    )
})
public class JpaArtifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id",
        updatable = false,
        nullable = false)
    private long id;

    @Column(name = "group_id",
        nullable = false)
    private String groupId;

    @Column(name = "artifact_id",
        nullable = false)
    private String artifactId;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "website")
    private String website;

    @Column(name = "git_repository")
    private String gitRepo;

    @Column(name = "issues")
    private String issues;

    public void setGroupId(final String groupId) {
        this.groupId = groupId;
    }

    public void setArtifactId(final String artifactId) {
        this.artifactId = artifactId;
    }

    public void setDisplayName(final String displayName) {
        this.displayName = displayName;
    }

    public void setWebsite(final String website) {
        this.website = website;
    }

    public void setGitRepo(final String gitRepo) {
        this.gitRepo = gitRepo;
    }

    public void setIssues(final String issues) {
        this.issues = issues;
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
            artifactId, that.artifactId) && Objects.equals(
            displayName, that.displayName) && Objects.equals(
            website, that.website) && Objects.equals(gitRepo, that.gitRepo) && Objects.equals(
            issues, that.issues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, groupId, artifactId, displayName, website, gitRepo, issues);
    }
}
